package com.clinconnect.chatbot.canonicalization;

import java.util.List;

/**
 * Result of a canonicalization lookup. Only Spring produces canonical IDs
 * (docs/09-SECURITY.md "Canonical IDs") — this type is how that resolution
 * is exposed to callers, including the AMBIGUOUS/NOT_FOUND outcomes that
 * later phases turn into clarification/NO_MATCH (FR-012, FR-014).
 */
public record CanonicalizationResult(CanonicalizationStatus status, String canonicalId, List<String> candidateIds) {

    public static CanonicalizationResult matched(String canonicalId) {
        return new CanonicalizationResult(CanonicalizationStatus.MATCHED, canonicalId, List.of());
    }

    public static CanonicalizationResult ambiguous(List<String> candidateIds) {
        return new CanonicalizationResult(CanonicalizationStatus.AMBIGUOUS, null, List.copyOf(candidateIds));
    }

    public static CanonicalizationResult notFound() {
        return new CanonicalizationResult(CanonicalizationStatus.NOT_FOUND, null, List.of());
    }
}
