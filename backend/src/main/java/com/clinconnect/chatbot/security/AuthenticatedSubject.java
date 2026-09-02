package com.clinconnect.chatbot.security;

/**
 * The authenticated caller identity. Never derived from client-supplied
 * data; see docs/09-SECURITY.md Session Security.
 */
public record AuthenticatedSubject(String subjectId, String role) {
}
