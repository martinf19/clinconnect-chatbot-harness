package com.clinconnect.chatbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RoleExplanationToolServiceTest {

    @Autowired
    private RoleExplanationToolService roleExplanationToolService;

    private final AuthenticatedSubject subject = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Test
    void returnsTheStoredApprovedDefinition() {
        ToolResult<OnCallRole> result = roleExplanationToolService.getRoleExplanation(subject, "PRIMARY_ONCALL");

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data().getDisplayName()).isEqualTo("Primary On-Call");
    }

    @Test
    void returnsNoMatchForAnUnknownRoleCode() {
        ToolResult<OnCallRole> result = roleExplanationToolService.getRoleExplanation(subject, "NOT_A_ROLE");

        assertThat(result.status()).isEqualTo(ToolResultStatus.NO_MATCH);
    }
}
