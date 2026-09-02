package com.clinconnect.chatbot.chat;

/** POST /api/v1/chat/messages request (docs/08-API-CONTRACTS.md). */
public record ChatMessageRequest(String sessionId, String clientMessageId, String message) {
}
