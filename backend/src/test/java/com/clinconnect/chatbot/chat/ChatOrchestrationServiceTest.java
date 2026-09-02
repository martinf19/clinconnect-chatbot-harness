package com.clinconnect.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinconnect.chatbot.domain.model.ContactType;
import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.domain.model.LocationSpecialty;
import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.domain.model.Provider;
import com.clinconnect.chatbot.domain.repository.CoverageAssignmentRepository;
import com.clinconnect.chatbot.domain.repository.LocationRepository;
import com.clinconnect.chatbot.domain.repository.LocationSpecialtyRepository;
import com.clinconnect.chatbot.domain.repository.OnCallRoleRepository;
import com.clinconnect.chatbot.domain.repository.ProviderRepository;
import com.clinconnect.chatbot.interpretation.ClarificationAnswer;
import com.clinconnect.chatbot.interpretation.ClarificationAnswerKind;
import com.clinconnect.chatbot.interpretation.InterpretRequest;
import com.clinconnect.chatbot.interpretation.InterpretResponse;
import com.clinconnect.chatbot.interpretation.InterpretationParameters;
import com.clinconnect.chatbot.interpretation.InterpretationStatus;
import com.clinconnect.chatbot.interpretation.InterpretationUnavailableException;
import com.clinconnect.chatbot.interpretation.ProviderReferenceKind;
import com.clinconnect.chatbot.interpretation.ProviderReferenceValue;
import com.clinconnect.chatbot.interpretation.PythonInterpretationClient;
import com.clinconnect.chatbot.interpretation.TimeExpressionValue;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.session.ClarificationParameterName;
import com.clinconnect.chatbot.session.ClarificationReason;
import com.clinconnect.chatbot.session.ConversationSession;
import com.clinconnect.chatbot.session.ConversationSessionStore;
import com.clinconnect.chatbot.session.LastQueryContext;
import com.clinconnect.chatbot.time.TimeExpressionKind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end orchestration tests: real H2-backed canonicalization/tool
 * execution/session state, with only {@link PythonInterpretationClient}
 * mocked (deterministic, no live Ollama/ai-service dependency —
 * docs/10-EVALUATION-PLAN.md "POC Test Runtime").
 *
 * <p>{@code getContactInfoClarificationPath*} tests specifically exercise
 * the get_contact_info clarification path per the residual Phase 2
 * extraction gap (planning/PHASE-STATUS.md Phase 2 deviation #7): Spring's
 * clarification mechanism must correctly recover a missing/ambiguous
 * provider reference regardless of how reliably Python extracted it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ChatOrchestrationServiceTest {

    @Autowired
    private ChatOrchestrationService orchestrationService;

    @Autowired
    private ConversationSessionStore sessionStore;

    @Autowired
    private CoverageAssignmentRepository coverageAssignmentRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationSpecialtyRepository locationSpecialtyRepository;

    @Autowired
    private OnCallRoleRepository onCallRoleRepository;

    @MockitoBean
    private PythonInterpretationClient pythonInterpretationClient;

    private static final AuthenticatedSubject USER = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    /** Within every fixed CoverageAssignment window used below (2026-08-30T00:00Z–08-31T00:00Z). */
    private static final Instant FIXED_NOW = Instant.parse("2026-08-30T20:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    private static InterpretationParameters params(
            String locationText, String specialtyText, ProviderReferenceValue providerReference) {
        return new InterpretationParameters(
                locationText, specialtyText, providerReference, null, null, null, null, null);
    }

    private static InterpretResponse interpreted(String intentId, InterpretationParameters parameters) {
        return new InterpretResponse(InterpretationStatus.INTERPRETED, intentId, parameters, List.of(), null);
    }

    /** A follow-up that states only a new time — e.g. "What about tomorrow?" (FR-013). */
    private static InterpretationParameters timeExpressionOnly(TimeExpressionKind kind) {
        return new InterpretationParameters(null, null, null, null, null, new TimeExpressionValue(kind, null), null, null);
    }

    private static InterpretResponse interpretedMissing(
            String intentId, InterpretationParameters parameters, ClarificationParameterName missing) {
        return new InterpretResponse(InterpretationStatus.INTERPRETED, intentId, parameters, List.of(missing), null);
    }

    private void whenMessage(String message, InterpretResponse response) {
        when(pythonInterpretationClient.interpret(
                        argThat(req -> req != null && req.message().equals(message) && req.pendingClarification() == null),
                        any()))
                .thenReturn(response);
    }

    private void whenClarificationReply(String message, InterpretResponse response) {
        when(pythonInterpretationClient.interpret(
                        argThat(req -> req != null && req.message().equals(message) && req.pendingClarification() != null),
                        any()))
                .thenReturn(response);
    }

    private ChatMessageRequest request(String sessionId, String message) {
        return new ChatMessageRequest(sessionId, UUID.randomUUID().toString(), message);
    }

    @Test
    void freshRequestWithNoParametersReturnsAnswer() {
        whenMessage("What locations can I search?", interpreted("get_locations", InterpretationParameters.empty()));

        ChatMessageResponse response =
                orchestrationService.handleMessage(USER, request(null, "What locations can I search?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(response.answerText()).contains("Oakland", "Antioch");
        assertThat(response.sessionId()).isNotBlank();
    }

    @Test
    void ambiguousLocationClarifiesThenResumesWithSelectedOption() {
        // Cardiology is offered at both seeded locations (SyntheticDataSeeder) — BACKEND_UNIQUE_OR_CLARIFY.
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-cardio-oakland", "provider-jordan-lee", "spec-cardiology", "loc-oakland",
                "PRIMARY_ONCALL", Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z")));

        whenMessage("Who is on call for Cardiology?", interpreted("get_oncall_now", params(null, "Cardiology", null)));

        ChatMessageResponse first =
                orchestrationService.handleMessage(USER, request(null, "Who is on call for Cardiology?"), "corr-1");

        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(first.clarification().reason()).isEqualTo(ClarificationReason.AMBIGUOUS_ENTITY);
        assertThat(first.clarification().parameter()).isEqualTo(ClarificationParameterName.LOCATION_TEXT);
        assertThat(first.clarification().options()).extracting("optionId")
                .containsExactlyInAnyOrder("loc-oakland", "loc-antioch");

        whenClarificationReply(
                "Oakland",
                new InterpretResponse(
                        null, null, null, null,
                        new ClarificationAnswer(ClarificationAnswerKind.SELECTED_OPTION, "loc-oakland", null)));

        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), "Oakland"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Jordan Lee");
    }

    @Test
    void missingParameterClarifiesThenResumesWithValueProvided() {
        whenMessage(
                "What specialties are available?",
                interpretedMissing("get_specialties", InterpretationParameters.empty(), ClarificationParameterName.LOCATION_TEXT));

        ChatMessageResponse first =
                orchestrationService.handleMessage(USER, request(null, "What specialties are available?"), "corr-1");

        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(first.clarification().reason()).isEqualTo(ClarificationReason.MISSING_PARAMETER);
        assertThat(first.clarification().parameter()).isEqualTo(ClarificationParameterName.LOCATION_TEXT);

        whenClarificationReply(
                "Oakland",
                new InterpretResponse(
                        null, null, null, null,
                        new ClarificationAnswer(ClarificationAnswerKind.VALUE_PROVIDED, null, "Oakland")));

        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), "Oakland"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Neurology", "Cardiology", "Pediatrics");
    }

    @Test
    void unresolvedAnswerReAsksTheSameClarificationWithoutExecutingATool() {
        whenMessage(
                "What specialties are available?",
                interpretedMissing("get_specialties", InterpretationParameters.empty(), ClarificationParameterName.LOCATION_TEXT));
        ChatMessageResponse first =
                orchestrationService.handleMessage(USER, request(null, "What specialties are available?"), "corr-1");

        whenClarificationReply(
                "orders",
                new InterpretResponse(
                        null, null, null, null, new ClarificationAnswer(ClarificationAnswerKind.UNRESOLVED, null, null)));

        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), "orders"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(second.clarification().parameter()).isEqualTo(ClarificationParameterName.LOCATION_TEXT);
        assertThat(sessionStore.find(first.sessionId())).get()
                .extracting(ConversationSession::pendingClarification)
                .satisfies(p -> assertThat(p).isPresent());
    }

    @Test
    void cancelClearsPendingClarification() {
        whenMessage(
                "What specialties are available?",
                interpretedMissing("get_specialties", InterpretationParameters.empty(), ClarificationParameterName.LOCATION_TEXT));
        ChatMessageResponse first =
                orchestrationService.handleMessage(USER, request(null, "What specialties are available?"), "corr-1");

        whenClarificationReply(
                "never mind",
                new InterpretResponse(
                        null, null, null, null, new ClarificationAnswer(ClarificationAnswerKind.CANCEL, null, null)));

        ChatMessageResponse second =
                orchestrationService.handleMessage(USER, request(first.sessionId(), "never mind"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(sessionStore.find(first.sessionId())).get()
                .extracting(ConversationSession::pendingClarification)
                .satisfies(p -> assertThat(p).isEmpty());
    }

    @Test
    void unrelatedReplacesThePendingClarificationWithANewIntent() {
        whenMessage(
                "What specialties are available?",
                interpretedMissing("get_specialties", InterpretationParameters.empty(), ClarificationParameterName.LOCATION_TEXT));
        ChatMessageResponse first =
                orchestrationService.handleMessage(USER, request(null, "What specialties are available?"), "corr-1");

        whenClarificationReply(
                "What locations can I search?",
                new InterpretResponse(
                        InterpretationStatus.INTERPRETED,
                        "get_locations",
                        InterpretationParameters.empty(),
                        List.of(),
                        new ClarificationAnswer(ClarificationAnswerKind.UNRELATED, null, null)));

        ChatMessageResponse second = orchestrationService.handleMessage(
                USER, request(first.sessionId(), "What locations can I search?"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Oakland", "Antioch");
    }

    @Test
    void getContactInfoClarificationPath_missingProviderReferenceThenResumes() {
        // Simulates the Phase 2 residual gap: Python did not extract a provider reference at
        // all. Spring must still recover correctly via clarification.
        whenMessage(
                "How can I reach the on-call clinician?",
                interpretedMissing(
                        "get_contact_info", InterpretationParameters.empty(), ClarificationParameterName.PROVIDER_REFERENCE));

        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, request(null, "How can I reach the on-call clinician?"), "corr-1");

        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(first.clarification().reason()).isEqualTo(ClarificationReason.MISSING_PARAMETER);
        assertThat(first.clarification().parameter()).isEqualTo(ClarificationParameterName.PROVIDER_REFERENCE);

        whenClarificationReply(
                "Dr. Avery Chen",
                new InterpretResponse(
                        null, null, null, null,
                        new ClarificationAnswer(ClarificationAnswerKind.VALUE_PROVIDED, null, "Dr. Avery Chen")));

        ChatMessageResponse second =
                orchestrationService.handleMessage(USER, request(first.sessionId(), "Dr. Avery Chen"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Avery Chen").contains("555-0104");
    }

    @Test
    void getContactInfoClarificationPath_ambiguousProviderNameThenResumesWithSelectedOption() {
        providerRepository.save(new Provider("provider-avery-chen-2", "Dr. Avery Chen", true));

        whenMessage(
                "What is Dr. Avery Chen's number?",
                interpreted(
                        "get_contact_info",
                        params(null, null, new ProviderReferenceValue(ProviderReferenceKind.EXPLICIT_TEXT, "Dr. Avery Chen"))));

        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, request(null, "What is Dr. Avery Chen's number?"), "corr-1");

        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(first.clarification().reason()).isEqualTo(ClarificationReason.AMBIGUOUS_ENTITY);
        assertThat(first.clarification().parameter()).isEqualTo(ClarificationParameterName.PROVIDER_REFERENCE);
        List<String> optionIds = first.clarification().options().stream().map(o -> o.optionId()).toList();
        assertThat(optionIds).containsExactlyInAnyOrder("provider-avery-chen", "provider-avery-chen-2");

        String chosen = optionIds.get(0);
        whenClarificationReply(
                "the first one",
                new InterpretResponse(
                        null, null, null, null,
                        new ClarificationAnswer(ClarificationAnswerKind.SELECTED_OPTION, chosen, null)));

        ChatMessageResponse second =
                orchestrationService.handleMessage(USER, request(first.sessionId(), "the first one"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
    }

    @Test
    void getContactInfoClarificationPath_missingContactTypeStillAnswersWithFullContactList() {
        // The residual Phase 2 gap: contact_type extraction is sometimes missed. contact_type
        // is optional (config/intents.yaml), so this must never block on a clarification —
        // it should just answer with everything on file.
        whenMessage(
                "What is the pager number for Dr. Avery Chen?",
                interpreted(
                        "get_contact_info",
                        params(null, null, new ProviderReferenceValue(ProviderReferenceKind.EXPLICIT_TEXT, "Dr. Avery Chen"))));

        ChatMessageResponse response = orchestrationService.handleMessage(
                USER, request(null, "What is the pager number for Dr. Avery Chen?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(response.answerText()).contains("555-0104").contains("555-0111");
    }

    @Test
    void getContactInfoClarificationPath_pronounWithNoSessionContextRequiresClarification() {
        whenMessage(
                "How can I reach them?",
                interpreted("get_contact_info", params(null, null, new ProviderReferenceValue(ProviderReferenceKind.LAST_RESULT_PROVIDER, null))));

        ChatMessageResponse response =
                orchestrationService.handleMessage(USER, request(null, "How can I reach them?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(response.clarification().parameter()).isEqualTo(ClarificationParameterName.PROVIDER_REFERENCE);
    }

    @Test
    void getContactInfoClarificationPath_pronounResolvesFromPriorSingleProviderResult() {
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-neuro-oakland", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z")));
        whenMessage(
                "Who is covering Neurology in Oakland?",
                interpreted("get_oncall_now", params("Oakland", "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, request(null, "Who is covering Neurology in Oakland?"), "corr-1");
        assertThat(first.status()).isEqualTo(ChatResponseStatus.ANSWER);

        whenMessage(
                "How can I reach them?",
                interpreted("get_contact_info", params(null, null, new ProviderReferenceValue(ProviderReferenceKind.LAST_RESULT_PROVIDER, null))));

        ChatMessageResponse second =
                orchestrationService.handleMessage(USER, request(first.sessionId(), "How can I reach them?"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Avery Chen");
    }

    @Test
    void duplicateMessageWithSameContentReturnsCachedResponseWithoutCallingPythonAgain() {
        whenMessage("What locations can I search?", interpreted("get_locations", InterpretationParameters.empty()));
        String clientMessageId = UUID.randomUUID().toString();
        ChatMessageRequest firstRequest = new ChatMessageRequest(null, clientMessageId, "What locations can I search?");

        ChatMessageResponse first = orchestrationService.handleMessage(USER, firstRequest, "corr-1");
        // A real client carries the returned session_id forward; retrying with sessionId=null
        // would legitimately mint a brand-new session with its own empty idempotency cache.
        ChatMessageRequest replayRequest =
                new ChatMessageRequest(first.sessionId(), clientMessageId, "What locations can I search?");
        ChatMessageResponse replay = orchestrationService.handleMessage(USER, replayRequest, "corr-2");

        assertThat(replay).isEqualTo(first);
        verify(pythonInterpretationClient, times(1)).interpret(any(), any());
    }

    @Test
    void duplicateMessageIdWithDifferentContentIsRejected() {
        whenMessage("What locations can I search?", interpreted("get_locations", InterpretationParameters.empty()));
        String clientMessageId = UUID.randomUUID().toString();
        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, new ChatMessageRequest(null, clientMessageId, "What locations can I search?"), "corr-1");

        whenMessage("Who is on call for Cardiology?", interpreted("get_oncall_now", params(null, "Cardiology", null)));

        org.junit.jupiter.api.Assertions.assertThrows(
                DuplicateMessageConflictException.class,
                () -> orchestrationService.handleMessage(
                        USER,
                        new ChatMessageRequest(first.sessionId(), clientMessageId, "Who is on call for Cardiology?"),
                        "corr-2"));
    }

    @Test
    void unknownSessionIdIsToleratedAndStartsAFreshSession() {
        whenMessage("What locations can I search?", interpreted("get_locations", InterpretationParameters.empty()));

        ChatMessageResponse response = orchestrationService.handleMessage(
                USER, request("does-not-exist-session-id", "What locations can I search?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(response.sessionId()).isNotEqualTo("does-not-exist-session-id");
    }

    @Test
    void sessionOwnedByAnotherSubjectIsRejected() {
        ConversationSession otherUsersSession = sessionStore.createSession("local-user-2");

        org.junit.jupiter.api.Assertions.assertThrows(
                SessionOwnershipException.class,
                () -> orchestrationService.handleMessage(
                        USER, request(otherUsersSession.sessionId(), "Who is on call for Cardiology?"), "corr-1"));
    }

    @Test
    void unauthorizedRoleFailsClosedWithErrorAndNeverExecutes() {
        // docs/09-SECURITY.md "Required Security Tests": unauthorized capability. The POC
        // dev-auth stub (DevAuthorizationService) grants every tool scope only to the
        // configured default role and denies everything else.
        AuthenticatedSubject wrongRole = new AuthenticatedSubject("local-user-1", "SOME_OTHER_ROLE");
        whenMessage("What locations can I search?", interpreted("get_locations", InterpretationParameters.empty()));

        ChatMessageResponse response =
                orchestrationService.handleMessage(wrongRole, request(null, "What locations can I search?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.ERROR);
    }

    @Test
    void unsupportedInterpretationNeverExecutesATool() {
        whenMessage(
                "The patient has severe chest pain. Is this urgent?",
                new InterpretResponse(InterpretationStatus.UNSUPPORTED, null, InterpretationParameters.empty(), List.of(), null));

        ChatMessageResponse response = orchestrationService.handleMessage(
                USER, request(null, "The patient has severe chest pain. Is this urgent?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.UNSUPPORTED);
    }

    @Test
    void noMatchWhenNothingCoversTheRequestedSpecialtyAndLocation() {
        whenMessage(
                "Who is on call for Pediatrics?",
                interpreted("get_oncall_now", params(null, "Pediatrics", null)));

        ChatMessageResponse response =
                orchestrationService.handleMessage(USER, request(null, "Who is on call for Pediatrics?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.NO_MATCH);
    }

    @Test
    void aiServiceFailureFailsClosedWithErrorAndAllowsRetryWithSameMessageId() {
        String clientMessageId = UUID.randomUUID().toString();
        when(pythonInterpretationClient.interpret(any(), any()))
                .thenThrow(new InterpretationUnavailableException("boom", null));

        ChatMessageResponse errorResponse = orchestrationService.handleMessage(
                USER, new ChatMessageRequest(null, clientMessageId, "Who is on call for Cardiology?"), "corr-1");
        assertThat(errorResponse.status()).isEqualTo(ChatResponseStatus.ERROR);

        org.mockito.Mockito.reset(pythonInterpretationClient);
        whenMessage("Who is on call for Cardiology?", interpreted("get_oncall_now", params(null, "Cardiology", null)));

        // Not cached as an error: the same client_message_id may be retried once the AI
        // service recovers (docs/09-SECURITY.md "Idempotency Security" exists to prevent
        // duplicate *execution*, not to block retrying a failed attempt).
        ChatMessageResponse retried = orchestrationService.handleMessage(
                USER,
                new ChatMessageRequest(errorResponse.sessionId(), clientMessageId, "Who is on call for Cardiology?"),
                "corr-2");
        assertThat(retried.status()).isNotEqualTo(ChatResponseStatus.ERROR);
    }

    // --- Phase 4: bounded follow-up context reuse (FR-013) ---------------------------------

    @Test
    void contextReuseFillsMissingSpecialtyFromLastQueryForFollowUpSchedule() {
        // "What about tomorrow?": Python only extracts the new time; specialty_text is reported
        // missing. Neurology is Oakland-only (SyntheticDataSeeder), so location_text stays
        // unresolved-but-unambiguous either way — this isolates missing_parameters auto-fill
        // from the separate BACKEND_UNIQUE_OR_CLARIFY location fallback (next test).
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-neuro-oakland-today", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z")));
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-neuro-oakland-tomorrow", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", Instant.parse("2026-08-31T07:00:00Z"), Instant.parse("2026-09-01T07:00:00Z")));

        whenMessage(
                "Who is on call for Neurology in Oakland?",
                interpreted("get_oncall_now", params("Oakland", "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, request(null, "Who is on call for Neurology in Oakland?"), "corr-1");
        assertThat(first.status()).isEqualTo(ChatResponseStatus.ANSWER);

        whenMessage(
                "What about tomorrow?",
                interpretedMissing(
                        "get_oncall_schedule",
                        timeExpressionOnly(TimeExpressionKind.TOMORROW),
                        ClarificationParameterName.SPECIALTY_TEXT));
        ChatMessageResponse second =
                orchestrationService.handleMessage(USER, request(first.sessionId(), "What about tomorrow?"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Avery Chen");
    }

    @Test
    void sameLocationReuseAutoResolvesAmbiguousLocationFromLastQuery() {
        // Cardiology is offered at both seeded locations (BACKEND_UNIQUE_OR_CLARIFY). Without
        // Phase 4 context reuse, omitting location on the second turn would re-clarify exactly
        // like ambiguousLocationClarifiesThenResumesWithSelectedOption above.
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-cardio-oakland-ctx", "provider-jordan-lee", "spec-cardiology", "loc-oakland",
                "PRIMARY_ONCALL", Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z")));

        whenMessage(
                "Who is on call for Cardiology in Oakland?",
                interpreted("get_oncall_now", params("Oakland", "Cardiology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, request(null, "Who is on call for Cardiology in Oakland?"), "corr-1");
        assertThat(first.status()).isEqualTo(ChatResponseStatus.ANSWER);

        whenMessage("Who is on call for Cardiology?", interpreted("get_oncall_now", params(null, "Cardiology", null)));
        ChatMessageResponse second = orchestrationService.handleMessage(
                USER, request(first.sessionId(), "Who is on call for Cardiology?"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Jordan Lee");
    }

    @Test
    void selectedOptionResumeForDuplicateLocationDisplayNamesUsesTrustedCanonicalId() {
        // Generalizes the Phase 3 PROVIDER_REFERENCE trusted-passthrough fix to LOCATION_TEXT:
        // Location.displayName is not schema-unique, so a second active "Oakland" must still
        // resolve to exactly the option the user picked (never re-canonicalized from its label).
        locationRepository.save(new Location("loc-oakland-2", "oakland-2", "Oakland", "America/Los_Angeles", true));
        locationSpecialtyRepository.save(new LocationSpecialty("loc-oakland-2", "spec-cardiology", true));
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-cardio-oakland2", "provider-avery-chen", "spec-cardiology", "loc-oakland-2",
                "PRIMARY_ONCALL", Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z")));

        whenMessage("Who is on call for Cardiology?", interpreted("get_oncall_now", params(null, "Cardiology", null)));
        ChatMessageResponse first =
                orchestrationService.handleMessage(USER, request(null, "Who is on call for Cardiology?"), "corr-1");

        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(first.clarification().parameter()).isEqualTo(ClarificationParameterName.LOCATION_TEXT);
        assertThat(first.clarification().options()).extracting("optionId")
                .containsExactlyInAnyOrder("loc-oakland", "loc-antioch", "loc-oakland-2");

        whenClarificationReply(
                "the second Oakland",
                new InterpretResponse(
                        null, null, null, null,
                        new ClarificationAnswer(ClarificationAnswerKind.SELECTED_OPTION, "loc-oakland-2", null)));
        ChatMessageResponse second = orchestrationService.handleMessage(
                USER, request(first.sessionId(), "the second Oakland"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Avery Chen");
    }

    @Test
    void selectedOptionResumeForDuplicateRoleDisplayNamesUsesTrustedCanonicalId() {
        // Same generalization for ROLE_TEXT, via the AMBIGUOUS_RESULT on-call-role path
        // (ambiguousOnCallRoleClarification): two active roles sharing a display name must
        // still resolve to exactly the option id chosen, not a re-canonicalized label.
        onCallRoleRepository.save(
                new OnCallRole("PRIMARY_ONCALL_2", "Primary On-Call", "Duplicate-named role for test coverage.", true));
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-neuro-oakland-primary", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z")));
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-neuro-oakland-primary2", "provider-jordan-lee", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL_2", Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z")));

        whenMessage(
                "Who is on call for Neurology in Oakland?",
                interpreted("get_oncall_now", params("Oakland", "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, request(null, "Who is on call for Neurology in Oakland?"), "corr-1");

        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(first.clarification().reason()).isEqualTo(ClarificationReason.AMBIGUOUS_RESULT);
        assertThat(first.clarification().parameter()).isEqualTo(ClarificationParameterName.ROLE_TEXT);
        assertThat(first.clarification().options()).extracting("optionId")
                .containsExactlyInAnyOrder("PRIMARY_ONCALL", "PRIMARY_ONCALL_2");

        whenClarificationReply(
                "the second one",
                new InterpretResponse(
                        null, null, null, null,
                        new ClarificationAnswer(ClarificationAnswerKind.SELECTED_OPTION, "PRIMARY_ONCALL_2", null)));
        ChatMessageResponse second = orchestrationService.handleMessage(
                USER, request(first.sessionId(), "the second one"), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains("Jordan Lee");
    }

    @Test
    void noMatchTransitionStillCarriesResolvedLocationAndSpecialtyForward() {
        // docs/07-DATA-MODEL.md "NO_MATCH: update query" — Phase 4 enriches that update with
        // whatever location/specialty *did* resolve, so a later "What about tomorrow?" can
        // still reuse it even though this particular turn found no coverage.
        whenMessage("Who is on call for Pediatrics?", interpreted("get_oncall_now", params(null, "Pediatrics", null)));

        ChatMessageResponse response =
                orchestrationService.handleMessage(USER, request(null, "Who is on call for Pediatrics?"), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.NO_MATCH);
        assertThat(sessionStore.find(response.sessionId())).get()
                .extracting(ConversationSession::lastQueryContext)
                .satisfies(ctx -> assertThat(ctx)
                        .contains(new LastQueryContext("get_oncall_now", "Oakland", "Pediatrics", null)));
    }
}
