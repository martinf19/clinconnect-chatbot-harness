package com.clinconnect.chatbot.canonicalization;

/** Outcome of resolving free-text/omitted input to a canonical persisted ID. */
public enum CanonicalizationStatus {
    MATCHED,
    AMBIGUOUS,
    NOT_FOUND
}
