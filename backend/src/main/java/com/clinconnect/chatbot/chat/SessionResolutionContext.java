package com.clinconnect.chatbot.chat;

import com.clinconnect.chatbot.session.ClarificationParameterName;
import com.clinconnect.chatbot.session.LastQueryContext;
import com.clinconnect.chatbot.session.LastResultContext;

/**
 * Everything {@link IntentOrchestrationService#resolve} may trust from the
 * session for one request: the last single-provider result (FR-013
 * pronoun resolution, Phase 3), the last resolved query (Phase 4 bounded
 * follow-up reuse), and — only when resuming a {@code SELECTED_OPTION}
 * clarification reply — the one parameter/canonical-id pair the user just
 * picked from Spring-supplied options.
 *
 * <p>{@code trustedSelection} generalizes the Phase 3 {@code
 * PROVIDER_REFERENCE} "pass the chosen option's canonical id straight
 * through" fix (planning/PHASE-STATUS.md Phase 3 deviation #3) to {@code
 * LOCATION_TEXT}/{@code ROLE_TEXT}: a selected option's id is already the
 * exact canonical id Spring itself generated when it built the
 * clarification, so re-deriving it by re-canonicalizing the option's
 * display-name label is both unnecessary and — if two locations/roles ever
 * shared a display name — unsafe (display names are not schema-unique;
 * see Location/OnCallRole).
 */
public record SessionResolutionContext(
        LastResultContext lastResult,
        LastQueryContext lastQuery,
        ClarificationParameterName trustedSelectionParameter,
        String trustedSelectionCanonicalId) {

    private static final SessionResolutionContext NONE = new SessionResolutionContext(null, null, null, null);

    public static SessionResolutionContext none() {
        return NONE;
    }

    public static SessionResolutionContext plain(LastResultContext lastResult, LastQueryContext lastQuery) {
        return new SessionResolutionContext(lastResult, lastQuery, null, null);
    }

    public static SessionResolutionContext trustedSelection(
            LastResultContext lastResult,
            LastQueryContext lastQuery,
            ClarificationParameterName parameter,
            String canonicalId) {
        return new SessionResolutionContext(lastResult, lastQuery, parameter, canonicalId);
    }

    public boolean isTrustedSelectionFor(ClarificationParameterName parameter) {
        return trustedSelectionParameter == parameter && trustedSelectionCanonicalId != null;
    }
}
