package com.clinconnect.chatbot.security;

/**
 * Replaceable local-authentication seam (docs/11-LOCAL-DEVELOPMENT.md:
 * "Use a replaceable development-auth provider. The local identity is not a
 * production security implementation."). Phase 0 provides only the
 * interface and a stub implementation; no endpoint calls this yet.
 */
public interface DevAuthenticationProvider {

    AuthenticatedSubject resolveCurrentSubject();
}
