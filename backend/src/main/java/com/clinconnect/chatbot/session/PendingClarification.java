package com.clinconnect.chatbot.session;

import com.clinconnect.chatbot.interpretation.InterpretationParameters;
import java.time.Instant;
import java.util.List;

/**
 * The single pending clarification a session may hold (FR-012: "exactly
 * one pending clarification exists per session"). {@code intentId} and
 * {@code rawParametersSoFar} are null only for a {@code LANGUAGE_UNCERTAIN}
 * clarification, which has no intent to resume — the next message is
 * simply reinterpreted as a fresh request. For every other reason, resuming
 * means overlaying the newly-resolved value onto {@code rawParametersSoFar}
 * and re-running the same per-intent handler from scratch (cheap, and
 * naturally handles a still-unresolved re-clarification without special
 * casing).
 */
public record PendingClarification(
        ClarificationReason reason,
        ClarificationParameterName parameter,
        List<ClarificationOption> options,
        String intentId,
        InterpretationParameters rawParametersSoFar,
        Instant createdAt) {
}
