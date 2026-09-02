package com.clinconnect.chatbot.interpretation;

/** Mirrors ai-service's InterpretRequest. */
public record InterpretRequest(
        String message, SessionContextPayload sessionContext, PendingClarificationPayload pendingClarification) {

    public static InterpretRequest of(String message) {
        return new InterpretRequest(message, null, null);
    }
}
