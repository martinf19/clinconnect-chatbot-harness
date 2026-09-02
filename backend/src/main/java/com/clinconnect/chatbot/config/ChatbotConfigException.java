package com.clinconnect.chatbot.config;

/**
 * Thrown when config/intents.yaml or config/tools.yaml is missing,
 * malformed, or contains an intent-to-tool mapping that does not resolve.
 * Deployment/configuration errors of this kind must fail closed
 * (docs/09-SECURITY.md Tool Security), so this is deliberately unchecked
 * and thrown during bean construction to abort application startup.
 */
public class ChatbotConfigException extends RuntimeException {

    public ChatbotConfigException(String message) {
        super(message);
    }

    public ChatbotConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
