package com.clinconnect.chatbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.domain.model.Specialty;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SpecialtyToolServiceTest {

    @Autowired
    private SpecialtyToolService specialtyToolService;

    private final AuthenticatedSubject subject = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Test
    void returnsOnlySpecialtiesOfferedAtTheGivenLocation() {
        ToolResult<java.util.List<Specialty>> result = specialtyToolService.getSpecialties(subject, "loc-oakland");

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).extracting(Specialty::getId)
                .containsExactlyInAnyOrder("spec-neurology", "spec-cardiology", "spec-pediatrics");
    }

    @Test
    void returnsEmptyListForALocationWithNoOfferedSpecialties() {
        ToolResult<java.util.List<Specialty>> result =
                specialtyToolService.getSpecialties(subject, "loc-does-not-exist");

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).isEmpty();
    }
}
