package com.clinconnect.chatbot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalDevAuthenticationProvider implements DevAuthenticationProvider {

    private final String defaultUser;
    private final String defaultRole;

    public LocalDevAuthenticationProvider(
            @Value("${clinconnect.dev-auth.default-user}") String defaultUser,
            @Value("${clinconnect.dev-auth.default-role}") String defaultRole) {
        this.defaultUser = defaultUser;
        this.defaultRole = defaultRole;
    }

    @Override
    public AuthenticatedSubject resolveCurrentSubject() {
        return new AuthenticatedSubject(defaultUser, defaultRole);
    }
}
