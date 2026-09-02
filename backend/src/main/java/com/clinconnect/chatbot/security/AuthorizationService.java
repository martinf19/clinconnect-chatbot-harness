package com.clinconnect.chatbot.security;

/**
 * Authorizes an authenticated subject against a tool's
 * {@code authorization_scope} (config/tools.yaml) before execution
 * (docs/09-SECURITY.md "Before tool execution... authorize the canonical
 * resource scope"). Replaceable seam: the POC stub grants scopes by role;
 * production replaces this with real RBAC/ABAC without changing callers.
 */
public interface AuthorizationService {

    void authorize(AuthenticatedSubject subject, String requiredScope);
}
