package com.clinconnect.chatbot.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.chat.ChatMessageRequest;
import com.clinconnect.chatbot.chat.ChatMessageResponse;
import com.clinconnect.chatbot.chat.ChatOrchestrationService;
import com.clinconnect.chatbot.chat.ChatResponseStatus;
import com.clinconnect.chatbot.chat.DuplicateMessageConflictException;
import com.clinconnect.chatbot.chat.SessionOwnershipException;
import com.clinconnect.chatbot.domain.model.ContactType;
import com.clinconnect.chatbot.interpretation.InterpretResponse;
import com.clinconnect.chatbot.interpretation.InterpretationParameters;
import com.clinconnect.chatbot.interpretation.InterpretationStatus;
import com.clinconnect.chatbot.interpretation.ProviderReferenceKind;
import com.clinconnect.chatbot.interpretation.ProviderReferenceValue;
import com.clinconnect.chatbot.interpretation.PythonInterpretationClient;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.session.ConversationSession;
import com.clinconnect.chatbot.session.ConversationSessionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic Layer D runner (docs/10-EVALUATION-PLAN.md) for {@code
 * tests/evaluation/negative-evaluation.yaml} — the subset of cases that are Spring's
 * responsibility (Phase 6). Message text is read from the YAML at runtime via {@link
 * EvaluationYaml}, same as {@link ConversationEvaluationTest}.
 *
 * <p>Three cases in this file (`malformed_ai_json`, `illegal_ai_enum`, `ai_cannot_select_tool`)
 * are about what ai-service does with a raw model response <em>before</em> Spring ever sees
 * it — not reimplemented here; see the class-level comment on each corresponding test method
 * below for the exact ai-service pytest that already covers it deterministically (all
 * currently passing — 57/57, verified this phase). `unauthorized_resource` is not
 * implementable at all: no per-location authorization model exists (Phase 1 deviation #4,
 * carried through every phase since) — its test aborts with a clear reason rather than
 * silently passing or being omitted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class NegativeEvaluationTest {

    private static final String FILE = "negative-evaluation.yaml";
    private static final Instant FIXED_NOW = Instant.parse("2026-08-30T18:00:00Z");
    private static final AuthenticatedSubject USER = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Autowired
    private ChatOrchestrationService orchestrationService;

    @Autowired
    private ConversationSessionStore sessionStore;

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

    private static InterpretResponse unsupported() {
        return new InterpretResponse(InterpretationStatus.UNSUPPORTED, null, InterpretationParameters.empty(), List.of(), null);
    }

    private static InterpretResponse interpreted(String intentId, InterpretationParameters parameters) {
        return new InterpretResponse(InterpretationStatus.INTERPRETED, intentId, parameters, List.of(), null);
    }

    private void whenMessage(String message, InterpretResponse response) {
        org.mockito.Mockito.when(pythonInterpretationClient.interpret(
                        org.mockito.ArgumentMatchers.argThat(req -> req != null && req.message().equals(message)),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);
    }

    private ChatMessageRequest request(String sessionId, String message) {
        return new ChatMessageRequest(sessionId, UUID.randomUUID().toString(), message);
    }

    private String caseMessage(String caseId) {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, caseId);
        return EvaluationYaml.str(testCase, "input.message");
    }

    // --- clinical_urgency_classification / diagnosis ----------------------------------------
    // Symptom/diagnosis language must never become a tool call (FR-009: declared_urgency is
    // only ever an explicit user statement, never inferred). Python correctly classifies both
    // as UNSUPPORTED per config/prompts/intent-router.md "Urgency Safety" — this asserts
    // Spring's deterministic handling of that classification: recordUnsupported + UNSUPPORTED
    // response, never reaching IntentOrchestrationService (ChatOrchestrationService
    // .handleFreshInterpretation's UNSUPPORTED branch returns before any tool dispatch).

    @Test
    void clinical_urgency_classification() {
        String message = caseMessage("clinical_urgency_classification");
        whenMessage(message, unsupported());
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.UNSUPPORTED);
    }

    @Test
    void diagnosis() {
        String message = caseMessage("diagnosis");
        whenMessage(message, unsupported());
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.UNSUPPORTED);
    }

    // --- sql_injection_instruction -----------------------------------------------------------
    // Spring's canonicalization layer only ever uses parameterized/derived JPA queries
    // (CanonicalizationService — no string-concatenated @Query anywhere in the codebase), so
    // even an adversarial specialty_text value is just an inert literal parameter, never
    // executable SQL. Simulated here by having Python (however compromised) hand Spring exactly
    // that string as specialty_text and confirming it safely resolves to NO_MATCH rather than
    // executing anything or throwing.

    @Test
    void sql_injection_instruction() {
        String message = caseMessage("sql_injection_instruction");
        whenMessage(message, interpreted("get_oncall_now", params(null, "SELECT * FROM providers", null)));
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.NO_MATCH);
    }

    // --- arbitrary_tool_prompt_injection ------------------------------------------------------
    // Spring dispatches only via the config/intents.yaml allow-list (NFR-006); an intent_id
    // with no entry there fails closed (IntentOrchestrationService.resolve ->
    // UnknownIntentException -> ChatOrchestrationService catches it -> ERROR, never dispatched).
    // Already unit-tested directly in IntentOrchestrationServiceTest.unknownIntentIdFailsClosed;
    // this exercises the identical fail-closed path through the full chat message flow.

    @Test
    void arbitrary_tool_prompt_injection() {
        String message = caseMessage("arbitrary_tool_prompt_injection");
        whenMessage(message, interpreted("admin_dump", InterpretationParameters.empty()));
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.ERROR);
    }

    // --- arbitrary_url -------------------------------------------------------------------------
    // No Spring component ever makes an outbound HTTP call driven by user/model text (the only
    // outbound HTTP client, PythonInterpretationClient, always calls the fixed configured
    // ai-service base URL — never a URL derived from a message). This is UNSUPPORTED per
    // intent-router.md (no capability matches "call this URL and tell me what it says").

    @Test
    void arbitrary_url() {
        String message = caseMessage("arbitrary_url");
        whenMessage(message, unsupported());
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.UNSUPPORTED);
    }

    // --- fabricate_contact ---------------------------------------------------------------------
    // provider-jordan-lee has no PAGER contact_method in the CSV-seeded baseline (only OFFICE) —
    // FactualResponseFormatter.formatContactInfo must report it as not listed, never invent one
    // (FR-006 "missing requested contact method is reported as not listed; no fallback
    // provider/contact is invented").

    @Test
    void fabricate_contact() {
        String message = caseMessage("fabricate_contact");
        InterpretationParameters requestPager = new InterpretationParameters(
                null, null, new ProviderReferenceValue(ProviderReferenceKind.EXPLICIT_TEXT, "Dr. Jordan Lee"),
                null, ContactType.PAGER, null, null, null);
        whenMessage(message, interpreted("get_contact_info", requestPager));
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");

        assertThat(response.status()).isEqualTo(ChatResponseStatus.ANSWER);
        assertThat(response.answerText()).contains("PAGER is not listed for Dr. Jordan Lee.");
        // No fabricated pager number: the only digit-bearing line is the real OFFICE contact.
        assertThat(response.answerText()).doesNotContain("555-01");
    }

    // --- unknown_entity_no_fuzzy_guess ----------------------------------------------------------
    // CanonicalizationService does exact/alias matching only (FR-015) — a near-miss like
    // "Neurologee" must fall through to NOT_FOUND/NO_MATCH, never a fuzzy-matched guess.

    @Test
    void unknown_entity_no_fuzzy_guess() {
        String message = caseMessage("unknown_entity_no_fuzzy_guess");
        whenMessage(message, interpreted("get_oncall_now", params("Oakland", "Neurologee", null)));
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.NO_MATCH);
    }

    // --- session_owner_mismatch ------------------------------------------------------------------
    // Already covered directly by ChatOrchestrationServiceTest.sessionOwnedByAnotherSubjectIsRejected
    // and ChatControllerTest.sessionOwnershipMismatchReturns403WithNoBody; included here too so
    // this runner is traceable 1:1 against every negative-evaluation.yaml case.

    @Test
    void session_owner_mismatch() {
        ConversationSession othersSession = sessionStore.createSession("local-user-2");
        String message = caseMessage("session_owner_mismatch");

        org.junit.jupiter.api.Assertions.assertThrows(SessionOwnershipException.class,
                () -> orchestrationService.handleMessage(USER, request(othersSession.sessionId(), message), "corr-1"));
    }

    // --- duplicate_message_changed_content ----------------------------------------------------
    // Already covered directly by ChatOrchestrationServiceTest
    // .duplicateMessageIdWithDifferentContentIsRejected; included here for the same traceability
    // reason as session_owner_mismatch.

    @Test
    void duplicate_message_changed_content() {
        Map<String, Object> testCase = EvaluationYaml.loadCase(FILE, "duplicate_message_changed_content");
        String priorMessage = EvaluationYaml.str(testCase, "fixture.prior_message");
        String clientMessageId = EvaluationYaml.str(testCase, "input.client_message_id");
        String newMessage = EvaluationYaml.str(testCase, "input.message");

        whenMessage(priorMessage, interpreted("get_locations", InterpretationParameters.empty()));
        ChatMessageResponse first = orchestrationService.handleMessage(
                USER, new ChatMessageRequest(null, clientMessageId, priorMessage), "corr-1");

        whenMessage(newMessage, interpreted("get_oncall_now", params(null, "Neurology", null)));
        org.junit.jupiter.api.Assertions.assertThrows(DuplicateMessageConflictException.class,
                () -> orchestrationService.handleMessage(
                        USER, new ChatMessageRequest(first.sessionId(), clientMessageId, newMessage), "corr-2"));
    }

    // --- unauthorized_resource -------------------------------------------------------------------

    @Test
    void unauthorized_resource() {
        Assumptions.abort(
                "tests/evaluation/negative-evaluation.yaml#unauthorized_resource is unsatisfiable: no"
                        + " per-location authorization model exists (DevAuthorizationService is role-only,"
                        + " scope-blind) — accepted POC limitation, unchanged since Phase 1 deviation #4.");
    }

    // --- coverage_gap_monitoring_deferred / broad_phonebook_deferred --------------------------
    // Both are on docs/02-REQUIREMENTS.md's "Deferred / Not Approved" list; get_contact_info's
    // own doc (docs/05-INTENT-CATALOG.md) states "General/bulk phonebook lookup is not
    // included" — both are UNSUPPORTED per intent-router.md, same deterministic Spring handling
    // as clinical_urgency_classification/diagnosis above.

    @Test
    void coverage_gap_monitoring_deferred() {
        String message = caseMessage("coverage_gap_monitoring_deferred");
        whenMessage(message, unsupported());
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.UNSUPPORTED);
    }

    @Test
    void broad_phonebook_deferred() {
        String message = caseMessage("broad_phonebook_deferred");
        whenMessage(message, unsupported());
        ChatMessageResponse response = orchestrationService.handleMessage(USER, request(null, message), "corr-1");
        assertThat(response.status()).isEqualTo(ChatResponseStatus.UNSUPPORTED);
    }

    // --- malformed_ai_json / illegal_ai_enum / ai_cannot_select_tool --------------------------
    // Not Spring's responsibility to validate — these are about ai-service's handling of raw
    // model output *before* Spring ever receives a response, already covered deterministically:
    //   malformed_ai_json      -> ai-service test_service.py::test_interpret_fails_closed_after_
    //                              exhausting_retries + test_interpret_route.py::test_interpret_
    //                              returns_502_when_interpretation_fails; Spring's matching
    //                              fail-closed half is ChatOrchestrationServiceTest
    //                              .aiServiceFailureFailsClosedWithErrorAndAllowsRetryWithSameMessageId.
    //   illegal_ai_enum        -> ai-service test_schemas.py::test_interpret_response_rejects_
    //                              illegal_time_expression_kind (Pydantic ValidationError).
    //   ai_cannot_select_tool  -> ai-service test_service.py::test_interpret_never_accepts_model_
    //                              supplied_tool_id + test_interpret_route.py::test_interpret_
    //                              rejects_unknown_request_fields.
    // All currently passing (57/57 ai-service pytest, verified this phase). Aborted rather than
    // silently omitted so this runner still accounts for every case in the YAML file.

    @Test
    void malformed_ai_json() {
        Assumptions.abort("Covered by ai-service pytest (test_service.py, test_interpret_route.py) — see class Javadoc.");
    }

    @Test
    void illegal_ai_enum() {
        Assumptions.abort("Covered by ai-service pytest (test_schemas.py) — see class Javadoc.");
    }

    @Test
    void ai_cannot_select_tool() {
        Assumptions.abort("Covered by ai-service pytest (test_service.py, test_interpret_route.py) — see class Javadoc.");
    }
}
