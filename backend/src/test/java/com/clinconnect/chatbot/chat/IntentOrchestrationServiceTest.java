package com.clinconnect.chatbot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinconnect.chatbot.interpretation.InterpretationParameters;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * NFR-006/docs/09-SECURITY.md "Tool Security" defense-in-depth: an intent_id
 * with no config/intents.yaml entry must fail closed rather than dispatch
 * anywhere. In normal operation this can't happen (ai-service's IntentId
 * enum is itself built from the same file, Phase 2) — this only guards
 * against a future contract drift.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class IntentOrchestrationServiceTest {

    @Autowired
    private IntentOrchestrationService intentOrchestrationService;

    private static final AuthenticatedSubject USER = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Test
    void unknownIntentIdFailsClosed() {
        assertThatThrownBy(() -> intentOrchestrationService.resolve(
                        "admin_dump", InterpretationParameters.empty(), USER, null))
                .isInstanceOf(UnknownIntentException.class);
    }

    @Test
    void getLocationsReturnsAnswerListingSeededLocations() {
        IntentOutcome outcome =
                intentOrchestrationService.resolve("get_locations", InterpretationParameters.empty(), USER, null);

        assertThat(outcome).isInstanceOf(IntentOutcome.Answer.class);
        assertThat(((IntentOutcome.Answer) outcome).text()).contains("Oakland", "Antioch");
    }

    @Test
    void getSpecialtiesForUnknownLocationIsNoMatch() {
        InterpretationParameters params = new InterpretationParameters(
                "Nowhereville", null, null, null, null, null, null, null);

        IntentOutcome outcome = intentOrchestrationService.resolve("get_specialties", params, USER, null);

        assertThat(outcome).isInstanceOf(IntentOutcome.NoMatch.class);
    }
}
