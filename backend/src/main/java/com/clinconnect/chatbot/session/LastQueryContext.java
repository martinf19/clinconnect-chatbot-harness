package com.clinconnect.chatbot.session;

/**
 * The most recently processed query, tracked per the docs/07-DATA-MODEL.md
 * state-transition contract (updated on every ANSWER/NO_MATCH). Phase 4
 * (planning/PLAN.md "Multi-Turn Conversation ... context ... pronoun
 * rules"): {@code locationText}/{@code specialtyText}/{@code roleText} carry
 * the canonical <em>display name</em> Spring itself resolved for the last
 * query (never raw/unvalidated user text), so a bounded follow-up like
 * "What about tomorrow?" or "Same location." can silently reuse them
 * (ChatOrchestrationService) without re-trusting anything Python supplies.
 * Each field is null when that parameter did not apply to the query that
 * produced this context (e.g. {@code get_locations} has none of them).
 */
public record LastQueryContext(String intentId, String locationText, String specialtyText, String roleText) {

    public static LastQueryContext of(String intentId) {
        return new LastQueryContext(intentId, null, null, null);
    }
}
