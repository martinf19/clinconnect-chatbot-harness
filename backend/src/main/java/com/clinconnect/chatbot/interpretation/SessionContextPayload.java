package com.clinconnect.chatbot.interpretation;

/**
 * Only the safe, non-sensitive subset of session state Python may see
 * (FR-013): never raw session internals.
 */
public record SessionContextPayload(LastResultPayload lastResult) {
}
