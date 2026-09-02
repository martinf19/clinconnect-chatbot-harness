package com.clinconnect.chatbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.domain.model.Department;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DepartmentInfoToolServiceTest {

    @Autowired
    private DepartmentInfoToolService departmentInfoToolService;

    private final AuthenticatedSubject subject = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Test
    void returnsTheApprovedDepartmentFields() {
        ToolResult<Department> result =
                departmentInfoToolService.getDepartmentInfo(subject, "loc-oakland", "spec-neurology");

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data().getId()).isEqualTo("dept-oakland-neurology");
        assertThat(result.data().getNote())
                .isEqualTo("Approved for new and established patient consult scheduling.");
    }

    @Test
    void returnsNoMatchWhenNoDepartmentIsPersistedForTheCombination() {
        ToolResult<Department> result =
                departmentInfoToolService.getDepartmentInfo(subject, "loc-oakland", "spec-cardiology");

        assertThat(result.status()).isEqualTo(ToolResultStatus.NO_MATCH);
        assertThat(result.data()).isNull();
    }
}
