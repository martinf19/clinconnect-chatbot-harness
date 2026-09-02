package com.clinconnect.chatbot.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryConversationSessionStoreTest {

    private static final Instant T0 = Instant.parse("2026-08-30T18:00:00Z");

    private static Clock mutableClockAt(AtomicReference<Instant> instant) {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return instant.get();
            }
        };
    }

    @Test
    void createdSessionIsFindableByItsOwner() {
        AtomicReference<Instant> now = new AtomicReference<>(T0);
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore(mutableClockAt(now), 1800, 20);

        ConversationSession created = store.createSession("local-user-1");

        assertThat(store.find(created.sessionId())).isPresent();
        assertThat(store.find(created.sessionId()).get().ownerSubjectId()).isEqualTo("local-user-1");
    }

    @Test
    void unknownSessionIdIsAbsent() {
        AtomicReference<Instant> now = new AtomicReference<>(T0);
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore(mutableClockAt(now), 1800, 20);

        assertThat(store.find("does-not-exist")).isEmpty();
    }

    @Test
    void nullSessionIdIsAbsent() {
        AtomicReference<Instant> now = new AtomicReference<>(T0);
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore(mutableClockAt(now), 1800, 20);

        assertThat(store.find(null)).isEmpty();
    }

    @Test
    void sessionBecomesUnfindableAfterTtlElapses() {
        AtomicReference<Instant> now = new AtomicReference<>(T0);
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore(mutableClockAt(now), 1800, 20);
        ConversationSession created = store.createSession("local-user-1");

        now.set(T0.plusSeconds(1801));

        assertThat(store.find(created.sessionId())).isEmpty();
    }

    @Test
    void sessionActivityExtendsItsEffectiveTtl() {
        AtomicReference<Instant> now = new AtomicReference<>(T0);
        InMemoryConversationSessionStore store = new InMemoryConversationSessionStore(mutableClockAt(now), 1800, 20);
        ConversationSession created = store.createSession("local-user-1");

        now.set(T0.plusSeconds(1700));
        created.recordUnsupported(now.get());
        now.set(T0.plusSeconds(1700 + 1700));

        assertThat(store.find(created.sessionId())).isPresent();
    }
}
