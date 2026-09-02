package com.clinconnect.chatbot.session;

import java.util.Optional;

/**
 * Replaceable seam (docs/03-ARCHITECTURE.md "Conversation-state
 * abstraction: POC -> Spring in-memory, Production -> Redis"). Business/
 * orchestration code depends only on this interface, never on the
 * in-memory implementation, so a future Redis-backed implementation can
 * replace {@link InMemoryConversationSessionStore} without touching
 * callers.
 */
public interface ConversationSessionStore {

    ConversationSession createSession(String ownerSubjectId);

    /**
     * Looks up a session, transparently treating an expired session as
     * absent (FR-013: "expired/mismatched session state is never
     * trusted"; docs/06-CONVERSATION-DESIGN.md "POC Restart Behavior": the
     * frontend must tolerate an expired/unknown session and start a new
     * one — Spring makes that the same code path).
     */
    Optional<ConversationSession> find(String sessionId);
}
