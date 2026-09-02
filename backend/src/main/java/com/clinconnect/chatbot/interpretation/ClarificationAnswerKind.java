package com.clinconnect.chatbot.interpretation;

/**
 * Classification of a reply to a pending clarification
 * (config/prompts/intent-router.md "Pending Clarification").
 */
public enum ClarificationAnswerKind {
    SELECTED_OPTION,
    VALUE_PROVIDED,
    CANCEL,
    UNRELATED,
    UNRESOLVED
}
