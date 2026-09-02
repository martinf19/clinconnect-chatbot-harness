package com.clinconnect.chatbot.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DevAuthorizationServiceTest {

    private final DevAuthorizationService service = new DevAuthorizationService("CHATBOT_USER");

    @Test
    void grantsAnyScopeToTheConfiguredPocRole() {
        assertThatCode(() -> service.authorize(new AuthenticatedSubject("local-user-1", "CHATBOT_USER"),
                "chatbot.locations.read"))
                .doesNotThrowAnyException();
    }

    @Test
    void deniesAnUnrecognizedRole() {
        assertThatThrownBy(() -> service.authorize(
                new AuthenticatedSubject("someone", "OTHER_ROLE"), "chatbot.locations.read"))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void failsClosedOnANullSubject() {
        assertThatThrownBy(() -> service.authorize(null, "chatbot.locations.read"))
                .isInstanceOf(AuthorizationDeniedException.class);
    }
}
