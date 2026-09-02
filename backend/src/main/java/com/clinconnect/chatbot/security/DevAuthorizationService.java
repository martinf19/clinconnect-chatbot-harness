package com.clinconnect.chatbot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * POC development authorization stub (docs/09-SECURITY.md "POC Security
 * Stubs": simple dev auth/authorization stubs are allowed, but Spring
 * remains the trusted boundary). Every scope is granted to the single POC
 * role configured via {@code clinconnect.dev-auth.default-role}; any other
 * role is denied. Production replaces this with real RBAC/ABAC behind the
 * same {@link AuthorizationService} interface.
 */
@Component
public class DevAuthorizationService implements AuthorizationService {

    private final String grantedRole;

    public DevAuthorizationService(@Value("${clinconnect.dev-auth.default-role}") String grantedRole) {
        this.grantedRole = grantedRole;
    }

    @Override
    public void authorize(AuthenticatedSubject subject, String requiredScope) {
        if (subject == null || !grantedRole.equals(subject.role())) {
            throw new AuthorizationDeniedException(
                    "Subject is not authorized for scope: " + requiredScope);
        }
    }
}
