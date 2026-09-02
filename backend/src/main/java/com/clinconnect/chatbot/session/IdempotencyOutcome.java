package com.clinconnect.chatbot.session;

/**
 * Result of checking a {@code client_message_id} against the session's
 * bounded processed-message cache (NFR-009, docs/09-SECURITY.md "Idempotency
 * Security").
 */
public sealed interface IdempotencyOutcome {

    /** No prior record of this client_message_id: proceed normally. */
    record New() implements IdempotencyOutcome {
    }

    /** Same ID, identical message: return the cached prior response unchanged. */
    record Replay(CachedMessage cached) implements IdempotencyOutcome {
    }

    /** Same ID, different message: suspicious/invalid — reject, do not execute. */
    record Conflict() implements IdempotencyOutcome {
    }
}
