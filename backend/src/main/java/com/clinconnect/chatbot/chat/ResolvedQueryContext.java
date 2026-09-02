package com.clinconnect.chatbot.chat;

/**
 * The canonical display text {@link IntentOrchestrationService} resolved
 * while handling one intent, if any — carried on {@link IntentOutcome
 * .Answer}/{@link IntentOutcome.NoMatch} so {@link ChatOrchestrationService}
 * can persist it into {@link com.clinconnect.chatbot.session.LastQueryContext}
 * for Phase 4 bounded follow-up reuse ("Same location.", "What about
 * tomorrow?"). Fields are null when that parameter did not apply to the
 * intent handled.
 */
public record ResolvedQueryContext(String locationText, String specialtyText, String roleText) {

    public static final ResolvedQueryContext EMPTY = new ResolvedQueryContext(null, null, null);
}
