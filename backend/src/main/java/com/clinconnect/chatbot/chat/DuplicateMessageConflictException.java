package com.clinconnect.chatbot.chat;

/**
 * Same client_message_id, different message content — "suspicious/invalid"
 * (docs/09-SECURITY.md "Idempotency Security"). Maps to HTTP 409; never
 * executes.
 */
public class DuplicateMessageConflictException extends RuntimeException {

    public DuplicateMessageConflictException() {
        super("client_message_id was already used with different message content");
    }
}
