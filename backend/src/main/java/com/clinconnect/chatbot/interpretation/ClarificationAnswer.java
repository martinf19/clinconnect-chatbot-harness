package com.clinconnect.chatbot.interpretation;

/** Mirrors ai-service's ClarificationAnswerResult. */
public record ClarificationAnswer(ClarificationAnswerKind answerKind, String selectedOptionId, String valueText) {
}
