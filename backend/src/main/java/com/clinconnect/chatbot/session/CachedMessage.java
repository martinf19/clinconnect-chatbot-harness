package com.clinconnect.chatbot.session;

/**
 * One bounded idempotency-cache entry (NFR-009). {@code responseBody} is
 * intentionally {@code Object}: the session package must not depend on the
 * chat package's response DTO, so it stores whatever the caller gives it
 * and hands the same instance back unchanged on a replay.
 */
public record CachedMessage(String clientMessageId, String requestMessage, String correlationId, Object responseBody) {
}
