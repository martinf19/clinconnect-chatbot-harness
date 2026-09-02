package com.clinconnect.chatbot.chat;

/**
 * A supplied session_id exists but belongs to a different authenticated
 * subject (docs/09-SECURITY.md "Session Security": "Mismatch: fail closed;
 * no state disclosure; no AI call; no tool execution"). Maps to HTTP 403.
 */
public class SessionOwnershipException extends RuntimeException {

    public SessionOwnershipException() {
        super("Session does not belong to the authenticated subject");
    }
}
