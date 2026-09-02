package com.clinconnect.chatbot.interpretation;

/**
 * Raised when the AI service cannot be reached or itself failed to produce
 * valid structured output (ai-service HTTP 502/503, timeout, or connection
 * failure). Never a chatbot answer — the orchestrator maps this to the
 * transport-neutral ERROR status (docs/06-CONVERSATION-DESIGN.md), never to
 * a fabricated ANSWER/NO_MATCH.
 */
public class InterpretationUnavailableException extends RuntimeException {

    public InterpretationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
