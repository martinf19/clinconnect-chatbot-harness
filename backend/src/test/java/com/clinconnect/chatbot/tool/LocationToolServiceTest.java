package com.clinconnect.chatbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LocationToolServiceTest {

    @Autowired
    private LocationToolService locationToolService;

    private final AuthenticatedSubject authorizedSubject = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    @Test
    void returnsOnlyActivePersistedLocations() {
        ToolResult<java.util.List<Location>> result = locationToolService.getLocations(authorizedSubject);

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).extracting(Location::getId)
                .containsExactlyInAnyOrder("loc-oakland", "loc-antioch");
    }

    @Test
    void deniesAnUnauthorizedSubject() {
        AuthenticatedSubject unauthorized = new AuthenticatedSubject("someone", "OTHER_ROLE");
        assertThatThrownBy(() -> locationToolService.getLocations(unauthorized))
                .isInstanceOf(AuthorizationDeniedException.class);
    }
}
