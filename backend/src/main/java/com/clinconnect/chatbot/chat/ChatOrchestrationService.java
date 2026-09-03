package com.clinconnect.chatbot.chat;

import com.clinconnect.chatbot.domain.model.DeclaredUrgency;
import com.clinconnect.chatbot.interpretation.ClarificationAnswer;
import com.clinconnect.chatbot.interpretation.ClarificationAnswerKind;
import com.clinconnect.chatbot.interpretation.InterpretRequest;
import com.clinconnect.chatbot.interpretation.InterpretResponse;
import com.clinconnect.chatbot.interpretation.InterpretationParameters;
import com.clinconnect.chatbot.interpretation.InterpretationUnavailableException;
import com.clinconnect.chatbot.interpretation.LastResultPayload;
import com.clinconnect.chatbot.interpretation.PendingClarificationPayload;
import com.clinconnect.chatbot.interpretation.ProviderReferenceKind;
import com.clinconnect.chatbot.interpretation.ProviderReferenceValue;
import com.clinconnect.chatbot.interpretation.PythonInterpretationClient;
import com.clinconnect.chatbot.interpretation.SessionContextPayload;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationDeniedException;
import com.clinconnect.chatbot.session.ClarificationOption;
import com.clinconnect.chatbot.session.ClarificationParameterName;
import com.clinconnect.chatbot.session.ClarificationReason;
import com.clinconnect.chatbot.session.ConversationSession;
import com.clinconnect.chatbot.session.ConversationSessionStore;
import com.clinconnect.chatbot.session.IdempotencyOutcome;
import com.clinconnect.chatbot.session.LastQueryContext;
import com.clinconnect.chatbot.session.LastResultContext;
import com.clinconnect.chatbot.session.PendingClarification;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements docs/03-ARCHITECTURE.md's "POC Request Ordering" end to end:
 * session validation/ownership, idempotency, calling Python interpretation,
 * clarification decision/resume, and the docs/07-DATA-MODEL.md state
 * transitions. This is the only caller of {@link PythonInterpretationClient}
 * and {@link IntentOrchestrationService}.
 *
 * <p><b>Clarification resume design:</b> a pending clarification stores the
 * intent and the raw {@link InterpretationParameters} as they stood at the
 * moment of clarification. Resuming overlays just the newly-resolved raw
 * value onto that snapshot and re-runs the exact same per-intent handler —
 * so a still-unresolved answer naturally produces a fresh (possibly
 * identical) clarification instead of needing special-case handling. For
 * {@code PROVIDER_REFERENCE}/{@code LOCATION_TEXT}/{@code ROLE_TEXT},
 * {@code SELECTED_OPTION} instead passes the chosen option's canonical id
 * straight through via {@link SessionResolutionContext}'s trusted-selection
 * fields (never re-trusting a raw label as if it were re-canonicalizable
 * text — see that class's Javadoc). Every other parameter overlays the
 * matching option's <em>label</em> and re-canonicalizes through the
 * identical path a typed answer would.
 *
 * <p><b>Phase 4 bounded context reuse (FR-013):</b> before deciding a
 * missing required parameter needs a fresh clarification, {@link
 * #dispatchInterpreted} first tries to silently backfill {@code
 * LOCATION_TEXT}/{@code SPECIALTY_TEXT}/{@code ROLE_TEXT} from the
 * session's {@link com.clinconnect.chatbot.session.LastQueryContext} (e.g.
 * "What about tomorrow?" reusing the last resolved specialty/location).
 * {@code TIME_EXPRESSION}/{@code PROVIDER_REFERENCE}/{@code CONTACT_TYPE}/
 * {@code DECLARED_URGENCY} are deliberately never auto-filled this way: time
 * must always come from the current message, provider pronouns already have
 * their own dedicated {@code LAST_RESULT_PROVIDER} mechanism (Phase 3), and
 * urgency is safety-sensitive (FR-009) and must always be explicit.
 *
 * <p><b>Known scope limit:</b> overlaying a raw answer for a
 * {@code TIME_EXPRESSION} clarification is not implemented — that would
 * require re-running semantic time classification, not a literal
 * substitution — so such a resume safely re-asks rather than guessing.
 * {@code CONTACT_TYPE}/{@code TIME_CONTEXT} never become a clarification
 * target in this implementation (both are always optional per
 * config/intents.yaml, so they can never appear in {@code
 * missing_parameters}, and this orchestrator never raises
 * AMBIGUOUS_ENTITY/AMBIGUOUS_RESULT for them).
 */
@Service
public class ChatOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);

    private static final String CANCELLATION_TEXT = "Okay — let me know if there's anything else I can help with.";

    private final ConversationSessionStore sessionStore;
    private final PythonInterpretationClient pythonInterpretationClient;
    private final IntentOrchestrationService intentOrchestrationService;
    private final Clock clock;

    public ChatOrchestrationService(
            ConversationSessionStore sessionStore,
            PythonInterpretationClient pythonInterpretationClient,
            IntentOrchestrationService intentOrchestrationService,
            Clock clock) {
        this.sessionStore = sessionStore;
        this.pythonInterpretationClient = pythonInterpretationClient;
        this.intentOrchestrationService = intentOrchestrationService;
        this.clock = clock;
    }

    public ChatMessageResponse handleMessage(AuthenticatedSubject subject, ChatMessageRequest request, String correlationId) {
        ConversationSession session = resolveSession(subject, request.sessionId());

        IdempotencyOutcome idempotency = session.checkIdempotency(request.clientMessageId(), request.message());
        if (idempotency instanceof IdempotencyOutcome.Replay replay) {
            return (ChatMessageResponse) replay.cached().responseBody();
        }
        if (idempotency instanceof IdempotencyOutcome.Conflict) {
            throw new DuplicateMessageConflictException();
        }

        long startNanos = System.nanoTime();
        ChatMessageResponse response;
        try {
            response = process(subject, session, request.message(), correlationId);
        } catch (InterpretationUnavailableException | AuthorizationDeniedException | UnknownIntentException e) {
            log.warn("Chat message processing failed closed: {}", e.getMessage());
            session.recordSystemError(clock.instant());
            response = ChatMessageResponse.error(session.sessionId(), correlationId);
            logRequestOutcome(correlationId, session, subject, response, startNanos, e.getClass().getSimpleName());
            // Deliberately not cached: this was not a successful execution, so the same
            // client_message_id may legitimately be retried (docs/09 Idempotency Security
            // exists to prevent duplicate *execution*, not to block retrying a failure).
            return response;
        }

        session.cacheResponse(request.clientMessageId(), request.message(), correlationId, response);
        logRequestOutcome(correlationId, session, subject, response, startNanos, null);
        return response;
    }

    /**
     * docs/09-SECURITY.md "Logging" baseline fields (Phase 7 POC hardening): timestamp/service
     * come from the logger itself; correlation ID, session ID, subject, result status, latency,
     * and a safe error category (just the failure's exception class name — never a message,
     * which could echo user input) are explicit here. intent_id/tool_id are logged separately
     * by IntentOrchestrationService (tagged with the same correlation ID via MDC) since they
     * are only known — and only meaningful — once an intent actually dispatches.
     */
    private void logRequestOutcome(
            String correlationId,
            ConversationSession session,
            AuthenticatedSubject subject,
            ChatMessageResponse response,
            long startNanos,
            String errorCategory) {
        long latencyMillis = (System.nanoTime() - startNanos) / 1_000_000;
        log.info(
                "chat_request correlation_id={} session_id={} subject={} status={} latency_ms={} error_category={}",
                correlationId, session.sessionId(), subject.subjectId(), response.status(), latencyMillis,
                errorCategory);
    }

    private ConversationSession resolveSession(AuthenticatedSubject subject, String requestedSessionId) {
        if (requestedSessionId == null) {
            return sessionStore.createSession(subject.subjectId());
        }
        Optional<ConversationSession> existing = sessionStore.find(requestedSessionId);
        if (existing.isEmpty()) {
            // Unknown/expired session_id: tolerate and start fresh rather than fail closed
            // (docs/06-CONVERSATION-DESIGN.md "POC Restart Behavior").
            return sessionStore.createSession(subject.subjectId());
        }
        ConversationSession session = existing.get();
        if (!session.ownerSubjectId().equals(subject.subjectId())) {
            // A *known* session belonging to someone else: fail closed, no state disclosure
            // (docs/09-SECURITY.md "Session Security").
            throw new SessionOwnershipException();
        }
        return session;
    }

    private ChatMessageResponse process(
            AuthenticatedSubject subject, ConversationSession session, String message, String correlationId) {
        Optional<PendingClarification> pendingOpt = session.pendingClarification();

        if (pendingOpt.isPresent() && pendingOpt.get().reason() != ClarificationReason.LANGUAGE_UNCERTAIN) {
            PendingClarification pending = pendingOpt.get();
            InterpretResponse python = callPythonForClarificationAnswer(session, pending, message, correlationId);
            return handleClarificationAnswer(subject, session, pending, python, correlationId);
        }

        if (pendingOpt.isPresent()) {
            // A LANGUAGE_UNCERTAIN clarification has no stored intent to resume; treat the
            // reply as a brand-new top-level request.
            session.cancelPendingClarification(clock.instant());
        }
        InterpretResponse python = callPythonForFreshInterpretation(session, message, correlationId);
        return handleFreshInterpretation(subject, session, python, correlationId);
    }

    private InterpretResponse callPythonForFreshInterpretation(
            ConversationSession session, String message, String correlationId) {
        InterpretRequest request = new InterpretRequest(message, buildSessionContextPayload(session), null);
        return pythonInterpretationClient.interpret(request, correlationId);
    }

    private InterpretResponse callPythonForClarificationAnswer(
            ConversationSession session, PendingClarification pending, String message, String correlationId) {
        PendingClarificationPayload payload =
                new PendingClarificationPayload(pending.reason().name(), pending.parameter(), pending.options());
        InterpretRequest request = new InterpretRequest(message, buildSessionContextPayload(session), payload);
        return pythonInterpretationClient.interpret(request, correlationId);
    }

    private SessionContextPayload buildSessionContextPayload(ConversationSession session) {
        return session.lastResultContext()
                .filter(r -> r.singleProviderId() != null)
                .map(r -> new SessionContextPayload(new LastResultPayload(true, r.providerDisplayName())))
                .orElse(null);
    }

    private ChatMessageResponse handleFreshInterpretation(
            AuthenticatedSubject subject, ConversationSession session, InterpretResponse python, String correlationId) {
        return switch (python.interpretationStatus()) {
            case UNSUPPORTED -> {
                session.recordUnsupported(clock.instant());
                yield ChatMessageResponse.unsupported(session.sessionId(), correlationId);
            }
            case NEEDS_LANGUAGE_CLARIFICATION -> {
                PendingClarification pending = new PendingClarification(
                        ClarificationReason.LANGUAGE_UNCERTAIN, null, List.of(), null, null, clock.instant());
                session.recordClarification(pending, clock.instant());
                yield ChatMessageResponse.clarification(
                        session.sessionId(), new ClarificationView(pending.reason(), null, List.of()), correlationId);
            }
            case INTERPRETED -> dispatchInterpreted(
                    subject, session, python.intentId(), python.parameters(), python.missingParameters(), correlationId);
        };
    }

    private ChatMessageResponse dispatchInterpreted(
            AuthenticatedSubject subject,
            ConversationSession session,
            String intentId,
            InterpretationParameters params,
            List<ClarificationParameterName> missingParameters,
            String correlationId) {
        LastQueryContext lastQuery = session.lastQueryContext().orElse(null);
        List<ClarificationParameterName> stillMissing =
                new ArrayList<>(missingParameters == null ? List.of() : missingParameters);
        InterpretationParameters filled = overlayFromLastQueryContext(params, stillMissing, lastQuery);

        if (!stillMissing.isEmpty()) {
            ClarificationParameterName firstMissing = stillMissing.get(0);
            PendingClarification pending = new PendingClarification(
                    ClarificationReason.MISSING_PARAMETER, firstMissing, List.of(), intentId, filled, clock.instant());
            session.recordClarification(pending, clock.instant());
            return ChatMessageResponse.clarification(
                    session.sessionId(),
                    new ClarificationView(pending.reason(), pending.parameter(), pending.options()),
                    correlationId);
        }

        SessionResolutionContext ctx =
                SessionResolutionContext.plain(session.lastResultContext().orElse(null), lastQuery);
        IntentOutcome outcome = intentOrchestrationService.resolve(intentId, filled, subject, ctx);
        return applyOutcome(session, intentId, filled, outcome, correlationId);
    }

    /**
     * Phase 4 FR-013 bounded context reuse: for each still-missing parameter that is one of
     * {@code LOCATION_TEXT}/{@code SPECIALTY_TEXT}/{@code ROLE_TEXT}, silently overlay the
     * session's last resolved value for it (never a raw/unvalidated value — {@link
     * LastQueryContext} only ever stores a canonical display name Spring itself resolved) and
     * drop it from {@code missing}, so the caller only clarifies for parameters that are
     * genuinely still unknown. See class Javadoc "Phase 4 bounded context reuse".
     */
    private InterpretationParameters overlayFromLastQueryContext(
            InterpretationParameters params, List<ClarificationParameterName> missing, LastQueryContext lastQuery) {
        if (lastQuery == null || missing.isEmpty()) {
            return params;
        }
        InterpretationParameters result = params;
        Iterator<ClarificationParameterName> it = missing.iterator();
        while (it.hasNext()) {
            ClarificationParameterName name = it.next();
            String contextValue = switch (name) {
                case LOCATION_TEXT -> lastQuery.locationText();
                case SPECIALTY_TEXT -> lastQuery.specialtyText();
                case ROLE_TEXT -> lastQuery.roleText();
                default -> null;
            };
            if (contextValue == null) {
                continue;
            }
            result = switch (name) {
                case LOCATION_TEXT -> result.withLocationText(contextValue);
                case SPECIALTY_TEXT -> result.withSpecialtyText(contextValue);
                case ROLE_TEXT -> result.withRoleText(contextValue);
                default -> result;
            };
            it.remove();
        }
        return result;
    }

    private ChatMessageResponse applyOutcome(
            ConversationSession session,
            String intentId,
            InterpretationParameters params,
            IntentOutcome outcome,
            String correlationId) {
        Instant now = clock.instant();
        if (outcome instanceof IntentOutcome.Answer answer) {
            session.recordSuccess(toLastQueryContext(intentId, answer.resolvedContext()), answer.newLastResultContext(), now);
            return ChatMessageResponse.answer(session.sessionId(), answer.text(), correlationId);
        }
        if (outcome instanceof IntentOutcome.Clarify clarify) {
            // params is stored so a resume can overlay just the newly-resolved value onto it
            // (see class Javadoc "Clarification resume design").
            PendingClarification pending = new PendingClarification(
                    clarify.reason(), clarify.parameter(), clarify.options(), intentId, params, now);
            session.recordClarification(pending, now);
            return ChatMessageResponse.clarification(
                    session.sessionId(),
                    new ClarificationView(clarify.reason(), clarify.parameter(), clarify.options()),
                    correlationId);
        }
        IntentOutcome.NoMatch noMatch = (IntentOutcome.NoMatch) outcome;
        session.recordNoMatch(toLastQueryContext(intentId, noMatch.resolvedContext()), now);
        return ChatMessageResponse.noMatch(session.sessionId(), correlationId);
    }

    private LastQueryContext toLastQueryContext(String intentId, ResolvedQueryContext resolved) {
        ResolvedQueryContext r = resolved == null ? ResolvedQueryContext.EMPTY : resolved;
        return new LastQueryContext(intentId, r.locationText(), r.specialtyText(), r.roleText());
    }

    private ChatMessageResponse handleClarificationAnswer(
            AuthenticatedSubject subject,
            ConversationSession session,
            PendingClarification pending,
            InterpretResponse python,
            String correlationId) {
        ClarificationAnswer answer = python.clarificationAnswer();
        if (answer == null) {
            // Python did not classify the reply against the pending clarification it was
            // given — a contract violation. Fail closed rather than guess.
            session.recordSystemError(clock.instant());
            return ChatMessageResponse.error(session.sessionId(), correlationId);
        }

        return switch (answer.answerKind()) {
            case CANCEL -> {
                session.cancelPendingClarification(clock.instant());
                yield ChatMessageResponse.answer(session.sessionId(), CANCELLATION_TEXT, correlationId);
            }
            case UNRESOLVED -> ChatMessageResponse.clarification(
                    session.sessionId(),
                    new ClarificationView(pending.reason(), pending.parameter(), pending.options()),
                    correlationId);
            case UNRELATED -> {
                session.cancelPendingClarification(clock.instant());
                yield handleFreshInterpretation(subject, session, python, correlationId);
            }
            case SELECTED_OPTION, VALUE_PROVIDED -> resumeWithOverlay(subject, session, pending, answer, correlationId);
        };
    }

    private ChatMessageResponse resumeWithOverlay(
            AuthenticatedSubject subject,
            ConversationSession session,
            PendingClarification pending,
            ClarificationAnswer answer,
            String correlationId) {
        if (answer.answerKind() == ClarificationAnswerKind.SELECTED_OPTION) {
            ClarificationOption matched = pending.options().stream()
                    .filter(o -> o.optionId().equals(answer.selectedOptionId()))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                // Defense-in-depth: never trust an option id Spring did not itself offer,
                // even though ai-service already validated this (CLAUDE.md Trust Rules).
                return ChatMessageResponse.clarification(
                        session.sessionId(),
                        new ClarificationView(pending.reason(), pending.parameter(), pending.options()),
                        correlationId);
            }
            if (pending.parameter() == ClarificationParameterName.PROVIDER_REFERENCE) {
                // A selected option's id is already the exact canonical provider id — never
                // re-derive it by re-canonicalizing the option's label as raw text. Two
                // providers can legitimately share a display name (that is exactly why
                // CanonicalizationService.resolveProvider can return AMBIGUOUS in the first
                // place), so overlaying the label could re-canonicalize to the same
                // ambiguous set instead of the one the user picked. Reusing the
                // LAST_RESULT_PROVIDER passthrough (trusted session-supplied id, never
                // re-canonicalized) sidesteps that entirely.
                InterpretationParameters overlaid = pending.rawParametersSoFar()
                        .withProviderReference(new ProviderReferenceValue(ProviderReferenceKind.LAST_RESULT_PROVIDER, null));
                LastResultContext selectedProvider = new LastResultContext(matched.optionId(), matched.label());
                SessionResolutionContext ctx = SessionResolutionContext.plain(
                        selectedProvider, session.lastQueryContext().orElse(null));
                IntentOutcome outcome = intentOrchestrationService.resolve(pending.intentId(), overlaid, subject, ctx);
                return applyOutcome(session, pending.intentId(), overlaid, outcome, correlationId);
            }
            if (pending.parameter() == ClarificationParameterName.LOCATION_TEXT
                    || pending.parameter() == ClarificationParameterName.ROLE_TEXT) {
                // Phase 4 generalization of the same trusted-passthrough fix above: a selected
                // location/role option's id is already the exact canonical id Spring itself
                // generated when it built the clarification options (ambiguousOrNotFoundLocation
                // Outcome / ambiguousOnCallRoleClarification). Location/OnCallRole display names
                // are not schema-unique (see SessionResolutionContext Javadoc), so re-deriving
                // the id by re-canonicalizing the option's label is unnecessary and, if two ever
                // shared a display name, unsafe — pass the id straight through instead.
                SessionResolutionContext ctx = SessionResolutionContext.trustedSelection(
                        session.lastResultContext().orElse(null),
                        session.lastQueryContext().orElse(null),
                        pending.parameter(),
                        matched.optionId());
                IntentOutcome outcome = intentOrchestrationService.resolve(
                        pending.intentId(), pending.rawParametersSoFar(), subject, ctx);
                return applyOutcome(session, pending.intentId(), pending.rawParametersSoFar(), outcome, correlationId);
            }
            return resumeWithRawTextOverlay(subject, session, pending, matched.label(), correlationId);
        }
        return resumeWithRawTextOverlay(subject, session, pending, answer.valueText(), correlationId);
    }

    private ChatMessageResponse resumeWithRawTextOverlay(
            AuthenticatedSubject subject,
            ConversationSession session,
            PendingClarification pending,
            String rawText,
            String correlationId) {
        InterpretationParameters overlaid = overlayRawValue(pending.rawParametersSoFar(), pending.parameter(), rawText);
        if (overlaid == null) {
            return ChatMessageResponse.clarification(
                    session.sessionId(),
                    new ClarificationView(pending.reason(), pending.parameter(), pending.options()),
                    correlationId);
        }

        SessionResolutionContext ctx = SessionResolutionContext.plain(
                session.lastResultContext().orElse(null), session.lastQueryContext().orElse(null));
        IntentOutcome outcome = intentOrchestrationService.resolve(pending.intentId(), overlaid, subject, ctx);
        return applyOutcome(session, pending.intentId(), overlaid, outcome, correlationId);
    }

    /** Null return means "cannot overlay this parameter" — caller re-asks rather than guesses. */
    private InterpretationParameters overlayRawValue(
            InterpretationParameters base, ClarificationParameterName parameter, String rawText) {
        return switch (parameter) {
            case LOCATION_TEXT -> base.withLocationText(rawText);
            case SPECIALTY_TEXT -> base.withSpecialtyText(rawText);
            case ROLE_TEXT -> base.withRoleText(rawText);
            case PROVIDER_REFERENCE ->
                    base.withProviderReference(new ProviderReferenceValue(ProviderReferenceKind.EXPLICIT_TEXT, rawText));
            case DECLARED_URGENCY -> {
                DeclaredUrgency parsed = parseEnum(DeclaredUrgency.class, rawText);
                yield parsed == null ? null : base.withDeclaredUrgency(parsed);
            }
            case CONTACT_TYPE, TIME_CONTEXT, TIME_EXPRESSION -> null;
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
