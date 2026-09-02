package com.clinconnect.chatbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.domain.model.ConsultRoutingRule;
import com.clinconnect.chatbot.domain.model.DeclaredUrgency;
import com.clinconnect.chatbot.domain.model.TimeContext;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConsultRoutingToolServiceTest {

    @Autowired
    private ConsultRoutingToolService consultRoutingToolService;

    private final AuthenticatedSubject subject = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Test
    void daytimeQueryReturnsTheDaytimeRuleAndAnyAlwaysApplicableRule() {
        // FR-008: a rule with a null time_context applies regardless of the
        // requested time_context, so the always-applicable "urgent" rule is
        // included alongside the DAYTIME-specific rule.
        ToolResult<List<ConsultRoutingRule>> result = consultRoutingToolService.getConsultRouting(
                subject, "loc-oakland", "spec-neurology", TimeContext.DAYTIME, null);

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).extracting(ConsultRoutingRule::getId)
                .containsExactlyInAnyOrder("rule-oakland-neurology-daytime", "rule-oakland-neurology-urgent");
    }

    @Test
    void omittedTimeContextReturnsAllActiveApplicableSections() {
        // FR-008: "If no time context is explicitly supplied, return all
        // active applicable routing sections rather than guessing."
        ToolResult<List<ConsultRoutingRule>> result = consultRoutingToolService.getConsultRouting(
                subject, "loc-oakland", "spec-neurology", null, null);

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).hasSize(3);
    }

    @Test
    void triageConsultIncludesTheUrgencySpecificRuleAndAnyUrgencyAgnosticRules() {
        // A rule with a null declared_urgency (like the daytime/after-hours
        // rules) is urgency-agnostic and still applies; only a rule tied to
        // the *other* declared urgency would be excluded (none exist in the
        // seeded data for this location/specialty).
        ToolResult<List<ConsultRoutingRule>> result = consultRoutingToolService.getConsultRouting(
                subject, "loc-oakland", "spec-neurology", null, DeclaredUrgency.URGENT);

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).extracting(ConsultRoutingRule::getId)
                .containsExactlyInAnyOrder(
                        "rule-oakland-neurology-daytime",
                        "rule-oakland-neurology-after-hours",
                        "rule-oakland-neurology-urgent");
    }

    @Test
    void returnsNoMatchWhenNoRoutingRuleIsPersistedForTheCombination() {
        ToolResult<List<ConsultRoutingRule>> result = consultRoutingToolService.getConsultRouting(
                subject, "loc-antioch", "spec-cardiology", null, null);

        assertThat(result.status()).isEqualTo(ToolResultStatus.NO_MATCH);
    }

    @Test
    void chartChatGuidanceReturnsTheStoredGuidance() {
        ToolResult<List<ConsultRoutingRule>> result =
                consultRoutingToolService.getChartChatGuidance(subject, "loc-oakland", "spec-neurology", null);

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).extracting(ConsultRoutingRule::getId).containsExactly("guidance-oakland-neurology");
    }

    @Test
    void chartChatGuidanceIsNoMatchWhenAbsent() {
        // FR-010: absent guidance -> NO_MATCH, nothing invented.
        ToolResult<List<ConsultRoutingRule>> result =
                consultRoutingToolService.getChartChatGuidance(subject, "loc-antioch", "spec-rheumatology", null);

        assertThat(result.status()).isEqualTo(ToolResultStatus.NO_MATCH);
    }
}
