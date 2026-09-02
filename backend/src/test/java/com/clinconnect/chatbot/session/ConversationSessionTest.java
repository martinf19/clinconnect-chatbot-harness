package com.clinconnect.chatbot.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversationSessionTest {

    private static final Instant T0 = Instant.parse("2026-08-30T18:00:00Z");

    @Test
    void newSessionIsNotExpiredImmediately() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);

        assertThat(session.isExpired(T0.plus(Duration.ofSeconds(1)), Duration.ofSeconds(1800))).isFalse();
    }

    @Test
    void sessionExpiresAfterTtlSinceLastActivity() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);

        assertThat(session.isExpired(T0.plus(Duration.ofSeconds(1801)), Duration.ofSeconds(1800))).isTrue();
    }

    @Test
    void firstMessageWithAnIdIsNew() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);

        assertThat(session.checkIdempotency("msg-1", "hello")).isInstanceOf(IdempotencyOutcome.New.class);
    }

    @Test
    void sameIdSameMessageReplaysCachedResponse() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);
        session.cacheResponse("msg-1", "hello", "corr-1", "cached-response");

        IdempotencyOutcome outcome = session.checkIdempotency("msg-1", "hello");

        assertThat(outcome).isInstanceOf(IdempotencyOutcome.Replay.class);
        assertThat(((IdempotencyOutcome.Replay) outcome).cached().responseBody()).isEqualTo("cached-response");
        assertThat(((IdempotencyOutcome.Replay) outcome).cached().correlationId()).isEqualTo("corr-1");
    }

    @Test
    void sameIdDifferentMessageIsAConflict() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);
        session.cacheResponse("msg-1", "hello", "corr-1", "cached-response");

        IdempotencyOutcome outcome = session.checkIdempotency("msg-1", "goodbye");

        assertThat(outcome).isInstanceOf(IdempotencyOutcome.Conflict.class);
    }

    @Test
    void idempotencyCacheIsBoundedAndEvictsOldestEntries() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 2);

        session.cacheResponse("msg-1", "one", "corr-1", "r1");
        session.cacheResponse("msg-2", "two", "corr-2", "r2");
        session.cacheResponse("msg-3", "three", "corr-3", "r3");

        assertThat(session.checkIdempotency("msg-1", "one")).isInstanceOf(IdempotencyOutcome.New.class);
        assertThat(session.checkIdempotency("msg-3", "three")).isInstanceOf(IdempotencyOutcome.Replay.class);
    }

    @Test
    void successTransitionClearsPendingClarificationAndReplacesContext() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);
        session.recordClarification(
                new PendingClarification(ClarificationReason.MISSING_PARAMETER, ClarificationParameterName.LOCATION_TEXT,
                        java.util.List.of(), "get_specialties", null, T0),
                T0);

        session.recordSuccess(
                LastQueryContext.of("get_oncall_now"),
                new LastResultContext("provider-1", "Dr. Test"),
                T0.plusSeconds(5));

        assertThat(session.pendingClarification()).isEmpty();
        assertThat(session.lastResultContext()).contains(new LastResultContext("provider-1", "Dr. Test"));
        assertThat(session.lastQueryContext()).contains(LastQueryContext.of("get_oncall_now"));
    }

    @Test
    void noMatchTransitionUpdatesQueryAndClearsResult() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);
        session.recordSuccess(
                LastQueryContext.of("get_oncall_now"), new LastResultContext("provider-1", "Dr. Test"), T0);

        session.recordNoMatch(LastQueryContext.of("get_oncall_now"), T0.plusSeconds(5));

        assertThat(session.lastResultContext()).isEmpty();
        assertThat(session.lastQueryContext()).isPresent();
    }

    @Test
    void unsupportedTransitionPreservesPriorQueryAndResult() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);
        session.recordSuccess(
                LastQueryContext.of("get_oncall_now"), new LastResultContext("provider-1", "Dr. Test"), T0);

        session.recordUnsupported(T0.plusSeconds(5));

        assertThat(session.lastResultContext()).contains(new LastResultContext("provider-1", "Dr. Test"));
        assertThat(session.lastQueryContext()).isPresent();
    }

    @Test
    void cancellationClearsOnlyThePendingClarification() {
        ConversationSession session = new ConversationSession("s1", "local-user-1", T0, 20);
        session.recordSuccess(
                LastQueryContext.of("get_oncall_now"), new LastResultContext("provider-1", "Dr. Test"), T0);
        session.recordClarification(
                new PendingClarification(ClarificationReason.MISSING_PARAMETER, ClarificationParameterName.LOCATION_TEXT,
                        java.util.List.of(), "get_specialties", null, T0),
                T0.plusSeconds(1));

        session.cancelPendingClarification(T0.plusSeconds(2));

        assertThat(session.pendingClarification()).isEmpty();
        assertThat(session.lastResultContext()).contains(new LastResultContext("provider-1", "Dr. Test"));
    }
}
