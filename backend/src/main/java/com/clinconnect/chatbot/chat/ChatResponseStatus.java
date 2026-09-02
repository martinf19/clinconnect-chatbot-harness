package com.clinconnect.chatbot.chat;

/** Frontend-visible chatbot response status (docs/06-CONVERSATION-DESIGN.md). */
public enum ChatResponseStatus {
    ANSWER,
    CLARIFICATION,
    NO_MATCH,
    UNSUPPORTED,
    ERROR
}
