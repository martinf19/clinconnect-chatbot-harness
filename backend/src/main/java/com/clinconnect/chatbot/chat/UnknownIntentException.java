package com.clinconnect.chatbot.chat;

/**
 * Python returned an intent_id with no entry in config/intents.yaml. Cannot
 * happen in normal operation (ai-service's IntentId enum is itself built
 * from the same file, Phase 2), but this is defense-in-depth per
 * docs/09-SECURITY.md "Tool Security": "A missing/unknown mapping is a
 * deployment/configuration error and fails closed."
 */
public class UnknownIntentException extends RuntimeException {

    public UnknownIntentException(String intentId) {
        super("No config/intents.yaml entry for intent_id: " + intentId);
    }
}
