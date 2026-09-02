package com.clinconnect.chatbot.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.chat.ChatMessageRequest;
import com.clinconnect.chatbot.chat.ChatMessageResponse;
import com.clinconnect.chatbot.chat.ChatOrchestrationService;
import com.clinconnect.chatbot.chat.ChatResponseStatus;
import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.model.LocationSpecialty;
import com.clinconnect.chatbot.domain.repository.CoverageAssignmentRepository;
import com.clinconnect.chatbot.domain.repository.LocationRepository;
import com.clinconnect.chatbot.domain.repository.LocationSpecialtyRepository;
import com.clinconnect.chatbot.domain.repository.ProviderRepository;
import com.clinconnect.chatbot.domain.repository.SpecialtyRepository;
import com.clinconnect.chatbot.interpretation.ClarificationAnswer;
import com.clinconnect.chatbot.interpretation.ClarificationAnswerKind;
import com.clinconnect.chatbot.interpretation.InterpretResponse;
import com.clinconnect.chatbot.interpretation.InterpretationParameters;
import com.clinconnect.chatbot.interpretation.InterpretationStatus;
import com.clinconnect.chatbot.interpretation.ProviderReferenceKind;
import com.clinconnect.chatbot.interpretation.ProviderReferenceValue;
import com.clinconnect.chatbot.interpretation.PythonInterpretationClient;
import com.clinconnect.chatbot.interpretation.TimeExpressionValue;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.session.ClarificationParameterName;
import com.clinconnect.chatbot.session.ConversationSession;
import com.clinconnect.chatbot.session.ConversationSessionStore;
import com.clinconnect.chatbot.session.LastQueryContext;
import com.clinconnect.chatbot.session.LastResultContext;
import com.clinconnect.chatbot.time.TimeExpressionKind;
import com.clinconnect.chatbot.time.TimeInterval;
import com.clinconnect.chatbot.time.TimeIntervalResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
 * Deterministic Layer B/C runner (docs/10-EVALUATION-PLAN.md) for {@code
 * tests/evaluation/conversation-evaluation.yaml} — Spring canonicalization/orchestration and
 * multi-turn conversation evaluation (Phase 6). Each test method is named after, and reads its
 * message text/expected values from, the YAML case with the same id via {@link EvaluationYaml}
 * — only the per-case fixture setup (which differs case to case: coverage assignments, extra
 * location_specialty rows, synthetic session state) is hand-written Java.
 *
 * <p>Real H2 + real canonicalization/tool/orchestration code, exactly like {@code
 * ChatOrchestrationServiceTest}; only {@link PythonInterpretationClient} is mocked, standing in
 * for a <em>correct</em> Layer-A interpretation of each turn's message — this suite tests
 * Spring's behavior <em>given</em> a correct interpretation, not whether the live model
 * produces one (that is intent-evaluation.yaml/Layer A, run separately and non-deterministically
 * via {@code ai-service/scripts/run_intent_evaluation.py}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ConversationEvaluationTest {

    private static final String FILE = "conversation-evaluation.yaml";
    // fixture_contract.clock: "2026-08-30T18:00:00-07:00"
    private static final Instant FIXED_NOW = OffsetDateTime.parse("2026-08-30T18:00:00-07:00").toInstant();
    private static final AuthenticatedSubject USER = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Autowired
    private ChatOrchestrationService orchestrationService;

    @Autowired
    private ConversationSessionStore sessionStore;

    @Autowired
    private CoverageAssignmentRepository coverageAssignmentRepository;

    @Autowired
    private LocationSpecialtyRepository locationSpecialtyRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ChatbotConfigLoader chatbotConfigLoader;

    @Autowired
    private TimeIntervalResolver timeIntervalResolver;

    @MockitoBean
    private PythonInterpretationClient pythonInterpretationClient;

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
        return new InterpretationParameters(locationText, specialtyText, providerReference, null, null, null, null, null);
    }

    private static InterpretationParameters timeParams(String locationText, String specialtyText, TimeExpressionKind kind) {
        return new InterpretationParameters(
                locationText, specialtyText, null, null, null, new TimeExpressionValue(kind, null), null, null);
    }

    private static InterpretResponse interpreted(String intentId, InterpretationParameters parameters) {
        return new InterpretResponse(InterpretationStatus.INTERPRETED, intentId, parameters, List.of(), null);
    }

    private static InterpretResponse interpretedMissing(
            String intentId, InterpretationParameters parameters, ClarificationParameterName missing) {
        return new InterpretResponse(InterpretationStatus.INTERPRETED, intentId, parameters, List.of(missing), null);
    }

    private void whenMessage(String message, InterpretResponse response) {
        org.mockito.Mockito.when(pythonInterpretationClient.interpret(
                        org.mockito.ArgumentMatchers.argThat(
                                req -> req != null && req.message().equals(message) && req.pendingClarification() == null),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);
    }

    private void whenClarificationReply(String message, InterpretResponse response) {
        org.mockito.Mockito.when(pythonInterpretationClient.interpret(
                        org.mockito.ArgumentMatchers.argThat(
                                req -> req != null && req.message().equals(message) && req.pendingClarification() != null),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);
    }

    private ChatMessageRequest request(String sessionId, String message) {
        return new ChatMessageRequest(sessionId, UUID.randomUUID().toString(), message);
    }

    private String locationDisplayName(String locationId) {
        return locationRepository.findById(locationId).orElseThrow().getDisplayName();
    }

    private String specialtyDisplayName(String specialtyId) {
        return specialtyRepository.findById(specialtyId).orElseThrow().getDisplayName();
    }

    private void assertToolMapping(String intentId, String expectedToolId) {
        assertThat(chatbotConfigLoader.config().intentsById().get(intentId).toolId()).isEqualTo(expectedToolId);
    }

    // --- alias_canonicalization ---------------------------------------------------------

    @Test
    void alias_canonicalization() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "alias_canonicalization");
        Map<String, Object> turn = EvaluationYaml.turns(testCase).get(0);
        String message = (String) turn.get("message");
        String expectedIntentId = EvaluationYaml.str(turn, "expected.intent_id");
        String expectedLocationId = EvaluationYaml.str(turn, "expected.canonical.location_id");
        String expectedSpecialtyId = EvaluationYaml.str(turn, "expected.canonical.specialty_id");
        String expectedToolId = EvaluationYaml.str(turn, "expected.tool_id");

        coverageAssignmentRepository.save(new CoverageAssignment(
                "eval-alias-oakland-neurology", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", FIXED_NOW.minusSeconds(3600), FIXED_NOW.plusSeconds(3600)));
        // "Neuro" is the raw alias text Python extracts (docs/05: "Do not manufacture IDs");
        // Spring's config/intents.yaml alias table resolves it, not Python.
        whenMessage(message, interpreted(expectedIntentId, params("Oakland", "Neuro", null)));

        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertCanonical(response.sessionId(), expectedLocationId, expectedSpecialtyId);
        assertToolMapping(expectedIntentId, expectedToolId);
    }

    // --- unique_location_auto_resolution -------------------------------------------------

    @Test
    void unique_location_auto_resolution() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "unique_location_auto_resolution");
        Map<String, Object> turn = EvaluationYaml.turns(testCase).get(0);
        String message = (String) turn.get("message");
        String expectedStatus = EvaluationYaml.str(turn, "expected.status");
        String expectedLocationId = EvaluationYaml.str(turn, "expected.canonical.location_id");
        String expectedSpecialtyId = EvaluationYaml.str(turn, "expected.canonical.specialty_id");

        // fixture.specialty_locations: spec-rheumatology -> [loc-antioch] already matches the
        // real CSV-seeded data (Rheumatology is Antioch-only) — no location_specialty override
        // needed for this case, only a coverage assignment so get_oncall_now has a match.
        coverageAssignmentRepository.save(new CoverageAssignment(
                "eval-unique-antioch-rheumatology", "provider-sam-patel", "spec-rheumatology", "loc-antioch",
                "PRIMARY_ONCALL", FIXED_NOW.minusSeconds(3600), FIXED_NOW.plusSeconds(3600)));
        whenMessage(message, interpreted("get_oncall_now", params(null, "Rheumatology", null)));

        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");

        assertThat(response.status().name()).isEqualTo(expectedStatus);
        assertThat(response.clarification()).isNull();
        assertCanonical(response.sessionId(), expectedLocationId, expectedSpecialtyId);
    }

    // --- ambiguous_location_clarification_and_resume --------------------------------------

    @Test
    void ambiguous_location_clarification_and_resume() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "ambiguous_location_clarification_and_resume");
        List<Map<String, Object>> turns = EvaluationYaml.turns(testCase);
        Map<String, Object> turn1 = turns.get(0);
        Map<String, Object> turn2 = turns.get(1);
        String message1 = (String) turn1.get("message");
        String message2 = (String) turn2.get("message");

        makeNeurologyAmbiguous();
        coverageAssignmentRepository.save(new CoverageAssignment(
                "eval-ambig-oakland-neurology", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", FIXED_NOW.minusSeconds(3600), FIXED_NOW.plusSeconds(3600)));

        whenMessage(message1, interpreted("get_oncall_now", params(null, "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(USER, request(null, message1), "corr-1");

        assertThat(first.status().name()).isEqualTo(EvaluationYaml.str(turn1, "expected.status"));
        assertThat(first.clarification().reason().name()).isEqualTo(EvaluationYaml.str(turn1, "expected.clarification.reason"));
        assertThat(first.clarification().parameter().wireValue())
                .isEqualTo(EvaluationYaml.str(turn1, "expected.clarification.parameter"));
        List<String> expectedOptionIds = EvaluationYaml.list(turn1, "expected.clarification.option_ids").stream()
                .map(Object::toString).toList();
        assertThat(first.clarification().options()).extracting("optionId")
                .containsExactlyInAnyOrderElementsOf(expectedOptionIds);

        whenClarificationReply(message2, new InterpretResponse(
                null, null, null, null,
                new ClarificationAnswer(ClarificationAnswerKind.SELECTED_OPTION, "loc-oakland", null)));
        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), message2), "corr-2");

        assertThat(second.status().name()).isEqualTo(EvaluationYaml.str(turn2, "expected.status"));
        assertCanonical(second.sessionId(),
                EvaluationYaml.str(turn2, "expected.canonical.location_id"),
                EvaluationYaml.str(turn2, "expected.canonical.specialty_id"));
        assertThat(sessionStore.find(second.sessionId())).get()
                .extracting(ConversationSession::pendingClarification)
                .satisfies(p -> assertThat(p).isEmpty());
    }

    // --- invalid_short_answer_does_not_guess -----------------------------------------------

    @Test
    void invalid_short_answer_does_not_guess() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "invalid_short_answer_does_not_guess");
        List<Map<String, Object>> turns = EvaluationYaml.turns(testCase);
        String message1 = (String) turns.get(0).get("message");
        String message2 = (String) turns.get(1).get("message");

        makeNeurologyAmbiguous();
        whenMessage(message1, interpreted("get_oncall_now", params(null, "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(USER, request(null, message1), "corr-1");
        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);

        whenClarificationReply(message2, new InterpretResponse(
                null, null, null, null, new ClarificationAnswer(ClarificationAnswerKind.UNRESOLVED, null, null)));
        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), message2), "corr-2");

        assertThat(second.status().name()).isEqualTo(EvaluationYaml.str(turns.get(1), "expected.status"));
        // "guessed_value: false" — re-asks the identical pending clarification rather than
        // resolving to any location.
        assertThat(second.clarification().parameter()).isEqualTo(first.clarification().parameter());
        assertThat(second.clarification().options()).extracting("optionId")
                .containsExactlyInAnyOrderElementsOf(first.clarification().options().stream()
                        .map(o -> o.optionId()).toList());
    }

    // --- clarification_cancel ---------------------------------------------------------------

    @Test
    void clarification_cancel() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "clarification_cancel");
        List<Map<String, Object>> turns = EvaluationYaml.turns(testCase);
        String message1 = (String) turns.get(0).get("message");
        String message2 = (String) turns.get(1).get("message");

        whenMessage(message1, interpretedMissing("get_specialties", InterpretationParameters.empty(),
                ClarificationParameterName.LOCATION_TEXT));
        ChatMessageResponse first = orchestrationService.handleMessage(USER, request(null, message1), "corr-1");
        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);
        assertThat(first.clarification().parameter().wireValue())
                .isEqualTo(EvaluationYaml.str(turns.get(0), "expected.clarification.parameter"));

        whenClarificationReply(message2, new InterpretResponse(
                null, null, null, null, new ClarificationAnswer(ClarificationAnswerKind.CANCEL, null, null)));
        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), message2), "corr-2");

        assertThat(sessionStore.find(second.sessionId())).get()
                .extracting(ConversationSession::pendingClarification)
                .satisfies(p -> assertThat(p).isEmpty());
        // tool_executed: false — a cancellation is always the fixed Spring-formatted ANSWER
        // text, never a tool result.
        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
    }

    // --- unrelated_replaces_pending -----------------------------------------------------------

    @Test
    void unrelated_replaces_pending() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "unrelated_replaces_pending");
        List<Map<String, Object>> turns = EvaluationYaml.turns(testCase);
        String message1 = (String) turns.get(0).get("message");
        String message2 = (String) turns.get(1).get("message");
        String expectedIntentId = EvaluationYaml.str(turns.get(1), "expected.intent_id");
        String expectedToolId = EvaluationYaml.str(turns.get(1), "expected.tool_id");

        whenMessage(message1, interpretedMissing("get_specialties", InterpretationParameters.empty(),
                ClarificationParameterName.LOCATION_TEXT));
        ChatMessageResponse first = orchestrationService.handleMessage(USER, request(null, message1), "corr-1");
        assertThat(first.status()).isEqualTo(ChatResponseStatus.CLARIFICATION);

        whenClarificationReply(message2, new InterpretResponse(
                InterpretationStatus.INTERPRETED, expectedIntentId, InterpretationParameters.empty(), List.of(),
                new ClarificationAnswer(ClarificationAnswerKind.UNRELATED, null, null)));
        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), message2), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(sessionStore.find(second.sessionId())).get()
                .extracting(ConversationSession::pendingClarification)
                .satisfies(p -> assertThat(p).isEmpty());
        assertToolMapping(expectedIntentId, expectedToolId);
    }

    // --- contact_followup_single_provider -----------------------------------------------------

    @Test
    void contact_followup_single_provider() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "contact_followup_single_provider");
        List<Map<String, Object>> turns = EvaluationYaml.turns(testCase);
        String message1 = (String) turns.get(0).get("message");
        String message2 = (String) turns.get(1).get("message");
        String expectedProviderId = EvaluationYaml.str(turns.get(0), "expected.last_result_context.single_provider_id");
        String expectedIntentId = EvaluationYaml.str(turns.get(1), "expected.intent_id");
        String expectedToolId = EvaluationYaml.str(turns.get(1), "expected.tool_id");

        coverageAssignmentRepository.save(new CoverageAssignment(
                "eval-contact-oakland-neurology", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", FIXED_NOW.minusSeconds(3600), FIXED_NOW.plusSeconds(3600)));
        whenMessage(message1, interpreted("get_oncall_now", params("Oakland", "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(USER, request(null, message1), "corr-1");
        assertThat(first.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(sessionStore.find(first.sessionId())).get()
                .extracting(ConversationSession::lastResultContext)
                .satisfies(r -> assertThat(r).get()
                        .extracting(LastResultContext::singleProviderId).isEqualTo(expectedProviderId));

        whenMessage(message2, interpreted(expectedIntentId,
                params(null, null, new ProviderReferenceValue(ProviderReferenceKind.LAST_RESULT_PROVIDER, null))));
        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), message2), "corr-2");

        assertThat(second.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(second.answerText()).contains(providerRepository.findById(expectedProviderId).orElseThrow().getDisplayName());
        assertToolMapping(expectedIntentId, expectedToolId);
    }

    // --- multi_provider_pronoun_requires_clarification --------------------------------------

    @Test
    void multi_provider_pronoun_requires_clarification() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "multi_provider_pronoun_requires_clarification");
        Map<String, Object> turn = EvaluationYaml.turns(testCase).get(0);
        String message = (String) turn.get("message");

        // fixture.last_result_context.single_provider_id: null — two prior providers, no single
        // resolvable one. Seeded directly since this is a *fixture*, not produced by a real
        // preceding turn (FR-013: "provider pronouns resolve only when
        // last_result_context.single_provider_id exists").
        ConversationSession session = sessionStore.createSession(USER.subjectId());
        session.recordSuccess(LastQueryContext.of("get_oncall_schedule"), new LastResultContext(null, null), FIXED_NOW);

        whenMessage(message, interpreted("get_contact_info",
                params(null, null, new ProviderReferenceValue(ProviderReferenceKind.LAST_RESULT_PROVIDER, null))));
        ChatMessageResponse response =
                orchestrationService.handleMessage(USER, request(session.sessionId(), message), "corr-1");

        assertThat(response.status().name()).isEqualTo(EvaluationYaml.str(turn, "expected.status"));
        assertThat(response.clarification().parameter()).isEqualTo(ClarificationParameterName.PROVIDER_REFERENCE);
    }

    // --- today_normalization / tonight_normalization / weekend_normalization_when_sunday -----
    // "normalized_time" is a pure Spring-internal computation (never serialized in the chat API
    // response), so these three call TimeIntervalResolver directly with the same fixed Clock the
    // rest of this suite uses — this is exactly "trusted time normalization" (docs/10 Layer B),
    // isolated from intent/canonicalization concerns already covered by the other cases.

    @Test
    void today_normalization() {
        assertNormalizedTime("today_normalization", TimeExpressionKind.TODAY);
    }

    @Test
    void tonight_normalization() {
        assertNormalizedTime("tonight_normalization", TimeExpressionKind.TONIGHT);
    }

    @Test
    void weekend_normalization_when_sunday() {
        assertNormalizedTime("weekend_normalization_when_sunday", TimeExpressionKind.WEEKEND);
    }

    private void assertNormalizedTime(String caseId, TimeExpressionKind kind) {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, caseId);
        Map<String, Object> turn = EvaluationYaml.turns(testCase).get(0);
        Instant expectedStart = OffsetDateTime.parse(EvaluationYaml.str(turn, "expected.normalized_time.start_at")).toInstant();
        Instant expectedEnd = OffsetDateTime.parse(EvaluationYaml.str(turn, "expected.normalized_time.end_at")).toInstant();

        TimeInterval interval = timeIntervalResolver.resolve(kind, null, ZoneId.of("America/Los_Angeles"));

        assertThat(interval.startAt()).as(caseId + " start_at").isEqualTo(expectedStart);
        assertThat(interval.endAt()).as(caseId + " end_at").isEqualTo(expectedEnd);
    }

    // --- user_correction_overrides_context ----------------------------------------------------

    @Test
    void user_correction_overrides_context() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "user_correction_overrides_context");
        List<Map<String, Object>> turns = EvaluationYaml.turns(testCase);
        String message1 = (String) turns.get(0).get("message");
        String message2 = (String) turns.get(1).get("message");

        whenMessage(message1, interpreted("get_oncall_now", params("Oakland", "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(USER, request(null, message1), "corr-1");
        assertCanonical(first.sessionId(), EvaluationYaml.str(turns.get(0), "expected.canonical.location_id"), null);

        // "Actually, Antioch." — location stated explicitly, specialty omitted; Phase 4 context
        // backfill (ChatOrchestrationService.overlayFromLastQueryContext) supplies "Neurology"
        // from the prior turn's LastQueryContext.
        whenMessage(message2, interpretedMissing("get_oncall_now", params("Antioch", null, null),
                ClarificationParameterName.SPECIALTY_TEXT));
        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), message2), "corr-2");

        assertCanonical(second.sessionId(),
                EvaluationYaml.str(turns.get(1), "expected.canonical.location_id"),
                EvaluationYaml.str(turns.get(1), "expected.canonical.specialty_id"));
    }

    // --- no_match_clears_last_result ----------------------------------------------------------

    @Test
    void no_match_clears_last_result() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "no_match_clears_last_result");
        List<Map<String, Object>> turns = EvaluationYaml.turns(testCase);
        String message1 = (String) turns.get(0).get("message");
        String message2 = (String) turns.get(1).get("message");

        coverageAssignmentRepository.save(new CoverageAssignment(
                "eval-nomatch-oakland-neurology", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", FIXED_NOW.minusSeconds(3600), FIXED_NOW.plusSeconds(3600)));
        whenMessage(message1, interpreted("get_oncall_now", params("Oakland", "Neurology", null)));
        ChatMessageResponse first = orchestrationService.handleMessage(USER, request(null, message1), "corr-1");
        assertThat(sessionStore.find(first.sessionId())).get()
                .extracting(ConversationSession::lastResultContext)
                .satisfies(r -> assertThat(r).get().extracting(LastResultContext::singleProviderId)
                        .isEqualTo(EvaluationYaml.str(turns.get(0), "expected.last_result_context.single_provider_id")));

        // fixture_result: NO_MATCH — deliberately no coverage assignment exists for
        // Antioch/Cardiology tonight.
        whenMessage(message2, interpreted("get_oncall_schedule", timeParams("Antioch", "Cardiology", TimeExpressionKind.TONIGHT)));
        ChatMessageResponse second = orchestrationService.handleMessage(USER, request(first.sessionId(), message2), "corr-2");

        assertThat(second.status().name()).isEqualTo(EvaluationYaml.str(turns.get(1), "expected.status"));
        assertThat(sessionStore.find(second.sessionId())).get().satisfies(session -> {
            assertThat(session.lastResultContext()).isEmpty();
            assertThat(session.lastQueryContext()).isPresent();
        });
    }

    // --- shared helpers --------------------------------------------------------------------

    /** Neurology is Oakland-only in the CSV-seeded baseline; several cases need it offered at
     * both seeded locations to exercise BACKEND_UNIQUE_OR_CLARIFY ambiguity, matching those
     * cases' own {@code fixture.specialty_locations} block. */
    private void makeNeurologyAmbiguous() {
        locationSpecialtyRepository.save(new LocationSpecialty("loc-antioch", "spec-neurology", true));
    }

    private void assertCanonical(String sessionId, String expectedLocationId, String expectedSpecialtyId) {
        assertThat(sessionStore.find(sessionId)).get().extracting(ConversationSession::lastQueryContext)
                .satisfies(ctx -> assertThat(ctx).get().satisfies(q -> {
                    if (expectedLocationId != null) {
                        assertThat(q.locationText()).isEqualTo(locationDisplayName(expectedLocationId));
                    }
                    if (expectedSpecialtyId != null) {
                        assertThat(q.specialtyText()).isEqualTo(specialtyDisplayName(expectedSpecialtyId));
                    }
                }));
    }
}
