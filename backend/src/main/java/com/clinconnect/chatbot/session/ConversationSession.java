package com.clinconnect.chatbot.session;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Spring-owned, in-memory, thread-safe conversation state for one session
 * (docs/07-DATA-MODEL.md "POC Conversation State": "session owner,
 * timestamps, last query context, last result context, one pending
 * clarification, and bounded processed-message/idempotency entries"). All
 * mutation is synchronized on the instance — POC-scale contention is low
 * and this keeps the state-transition rules (below) atomic and easy to
 * verify against docs/07's "State Transitions" table.
 *
 * <p>Restart loses all of this; that is accepted POC behavior
 * (docs/07-DATA-MODEL.md "Restart Behavior").
 */
public final class ConversationSession {

    private final String sessionId;
    private final String ownerSubjectId;
    private final Instant createdAt;
    private final int maxProcessedMessages;

    private Instant lastActivityAt;
    private PendingClarification pendingClarification;
    private LastQueryContext lastQueryContext;
    private LastResultContext lastResultContext;

    private final Map<String, CachedMessage> processedMessages;

    public ConversationSession(String sessionId, String ownerSubjectId, Instant createdAt, int maxProcessedMessages) {
        this.sessionId = sessionId;
        this.ownerSubjectId = ownerSubjectId;
        this.createdAt = createdAt;
        this.lastActivityAt = createdAt;
        this.maxProcessedMessages = maxProcessedMessages;
        this.processedMessages = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
                return size() > ConversationSession.this.maxProcessedMessages;
            }
        };
    }

    public String sessionId() {
        return sessionId;
    }

    public String ownerSubjectId() {
        return ownerSubjectId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized Instant lastActivityAt() {
        return lastActivityAt;
    }

    public synchronized boolean isExpired(Instant now, Duration ttl) {
        return lastActivityAt.plus(ttl).isBefore(now);
    }

    public synchronized Optional<PendingClarification> pendingClarification() {
        return Optional.ofNullable(pendingClarification);
    }

    public synchronized Optional<LastQueryContext> lastQueryContext() {
        return Optional.ofNullable(lastQueryContext);
    }

    public synchronized Optional<LastResultContext> lastResultContext() {
        return Optional.ofNullable(lastResultContext);
    }

    /** docs/07 state transition "clarification": update pending clarification/activity only. */
    public synchronized void recordClarification(PendingClarification newPendingClarification, Instant now) {
        this.pendingClarification = Objects.requireNonNull(newPendingClarification);
        this.lastActivityAt = now;
    }

    /** docs/07 state transition "success": clear clarification, replace query/result context. */
    public synchronized void recordSuccess(LastQueryContext query, LastResultContext result, Instant now) {
        this.pendingClarification = null;
        this.lastQueryContext = query;
        this.lastResultContext = result;
        this.lastActivityAt = now;
    }

    /** docs/07 state transition "NO_MATCH": update query, clear result. */
    public synchronized void recordNoMatch(LastQueryContext query, Instant now) {
        this.pendingClarification = null;
        this.lastQueryContext = query;
        this.lastResultContext = null;
        this.lastActivityAt = now;
    }

    /** docs/07 state transition "UNSUPPORTED": preserve prior query/result. */
    public synchronized void recordUnsupported(Instant now) {
        this.pendingClarification = null;
        this.lastActivityAt = now;
    }

    /** docs/07 state transition "cancellation": clear pending clarification only. */
    public synchronized void cancelPendingClarification(Instant now) {
        this.pendingClarification = null;
        this.lastActivityAt = now;
    }

    /**
     * docs/07 state transition "validation/system error": no query/result
     * change — and deliberately not cached in the idempotency map (see
     * {@link #checkIdempotency}), so a genuinely failed attempt may be
     * retried with the same client_message_id.
     */
    public synchronized void recordSystemError(Instant now) {
        this.lastActivityAt = now;
    }

    /** NFR-009 / docs/09 "Idempotency Security". */
    public synchronized IdempotencyOutcome checkIdempotency(String clientMessageId, String message) {
        CachedMessage cached = processedMessages.get(clientMessageId);
        if (cached == null) {
            return new IdempotencyOutcome.New();
        }
        if (cached.requestMessage().equals(message)) {
            return new IdempotencyOutcome.Replay(cached);
        }
        return new IdempotencyOutcome.Conflict();
    }

    public synchronized void cacheResponse(
            String clientMessageId, String message, String correlationId, Object responseBody) {
        processedMessages.put(clientMessageId, new CachedMessage(clientMessageId, message, correlationId, responseBody));
    }
}
