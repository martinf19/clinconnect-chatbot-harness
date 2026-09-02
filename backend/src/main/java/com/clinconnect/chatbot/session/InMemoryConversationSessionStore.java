package com.clinconnect.chatbot.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * POC implementation of {@link ConversationSessionStore}: a bounded-by-TTL,
 * thread-safe, in-memory map (docs/07-DATA-MODEL.md). Lost on Spring
 * restart — accepted POC behavior (docs/07 "Restart Behavior").
 */
@Component
public class InMemoryConversationSessionStore implements ConversationSessionStore {

    private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;
    private final int maxProcessedMessages;

    public InMemoryConversationSessionStore(
            Clock clock,
            @Value("${clinconnect.session.ttl-seconds}") long ttlSeconds,
            @Value("${clinconnect.session.max-processed-messages}") int maxProcessedMessages) {
        this.clock = clock;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.maxProcessedMessages = maxProcessedMessages;
    }

    @Override
    public ConversationSession createSession(String ownerSubjectId) {
        String sessionId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        ConversationSession session = new ConversationSession(sessionId, ownerSubjectId, now, maxProcessedMessages);
        sessions.put(sessionId, session);
        return session;
    }

    @Override
    public Optional<ConversationSession> find(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        ConversationSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(clock.instant(), ttl)) {
            sessions.remove(sessionId, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }
}
