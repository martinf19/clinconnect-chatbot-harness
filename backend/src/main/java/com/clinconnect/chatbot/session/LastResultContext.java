package com.clinconnect.chatbot.session;

/**
 * Bounded, safe context about the most recent successful answer (FR-013).
 * {@code singleProviderId} is only ever set when exactly one provider was
 * the subject of the last answer — the only case
 * {@code provider_reference.kind = LAST_RESULT_PROVIDER} is allowed to
 * resolve (docs/05-INTENT-CATALOG.md).
 */
public record LastResultContext(String singleProviderId, String providerDisplayName) {
}
