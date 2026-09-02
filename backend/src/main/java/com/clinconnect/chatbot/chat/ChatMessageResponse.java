package com.clinconnect.chatbot.chat;

/**
 * POST /api/v1/chat/messages response. Not fully dictated by
 * docs/08-API-CONTRACTS.md (that file only shows the request shape) — this
 * is this implementation's own design within the documented constraints
 * (snake_case via the global Jackson naming strategy, the canonical
 * ChatResponseStatus/ClarificationView shapes). See planning/PHASE-STATUS.md
 * for the exact field list.
 */
public record ChatMessageResponse(
        String sessionId,
        ChatResponseStatus status,
        String answerText,
        ClarificationView clarification,
        String correlationId) {

    public static ChatMessageResponse answer(String sessionId, String answerText, String correlationId) {
        return new ChatMessageResponse(sessionId, ChatResponseStatus.ANSWER, answerText, null, correlationId);
    }

    public static ChatMessageResponse clarification(String sessionId, ClarificationView clarification, String correlationId) {
        return new ChatMessageResponse(sessionId, ChatResponseStatus.CLARIFICATION, null, clarification, correlationId);
    }

    public static ChatMessageResponse noMatch(String sessionId, String correlationId) {
        return new ChatMessageResponse(sessionId, ChatResponseStatus.NO_MATCH, null, null, correlationId);
    }

    public static ChatMessageResponse unsupported(String sessionId, String correlationId) {
        return new ChatMessageResponse(sessionId, ChatResponseStatus.UNSUPPORTED, null, null, correlationId);
    }

    public static ChatMessageResponse error(String sessionId, String correlationId) {
        return new ChatMessageResponse(sessionId, ChatResponseStatus.ERROR, null, null, correlationId);
    }
}
