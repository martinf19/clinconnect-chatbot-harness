package com.clinconnect.chatbot.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TimeIntervalResolverTest {

    private final ZoneId zone = ZoneId.of("America/Los_Angeles");

    // 2026-06-15 is a Monday, 12:00 local time.
    private final Clock mondayNoon =
            Clock.fixed(Instant.parse("2026-06-15T19:00:00Z"), ZoneId.of("UTC"));

    @Test
    void todayIsTheFullCalendarDayInLocationZone() {
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayNoon, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.TODAY, null, zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-15T07:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-16T07:00:00Z"));
    }

    @Test
    void tonightStartsAtTheConfiguredLocalTime() {
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayNoon, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.TONIGHT, null, zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-16T00:00:00Z")); // 17:00 PDT
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-16T07:00:00Z")); // next midnight PDT
    }

    @Test
    void tomorrowIsTheNextFullCalendarDay() {
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayNoon, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.TOMORROW, null, zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-16T07:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-17T07:00:00Z"));
    }

    @Test
    void weekendFromAWeekdayIsTheUpcomingSaturdayThroughMonday() {
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayNoon, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.WEEKEND, null, zone);

        // Monday 2026-06-15 -> upcoming Saturday is 2026-06-20.
        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-20T07:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-22T07:00:00Z"));
    }

    @Test
    void weekendFromWithinTheWeekendIsTheCurrentSaturdayThroughMonday() {
        // 2026-06-20 is a Saturday.
        Clock saturdayClock = Clock.fixed(Instant.parse("2026-06-20T19:00:00Z"), ZoneId.of("UTC"));
        TimeIntervalResolver resolver = new TimeIntervalResolver(saturdayClock, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.WEEKEND, null, zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-20T07:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-22T07:00:00Z"));
    }

    // --- Weekend boundary regression: Friday, Saturday, Sunday, Monday around one weekend ---
    // (planning/PHASE-STATUS.md Phase 6 finding "weekend_normalization_when_sunday" — no
    // existing test exercised a Sunday anchor before that). 2026-06-20/21/22 are Sat/Sun/Mon;
    // every anchor day here refers to that same Saturday-through-Monday weekend except the
    // Monday case, which is already past it and must roll to the *next* one.

    @Test
    void weekendFromFridayIsTheImmediatelyUpcomingSaturdayThroughMonday() {
        // 2026-06-19 is the Friday immediately before the 2026-06-20 weekend.
        Clock fridayClock = Clock.fixed(Instant.parse("2026-06-19T19:00:00Z"), ZoneId.of("UTC"));
        TimeIntervalResolver resolver = new TimeIntervalResolver(fridayClock, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.WEEKEND, null, zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-20T07:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-22T07:00:00Z"));
    }

    @Test
    void weekendFromSundayIsTheWeekendThatStartedYesterdayNotNextWeeks() {
        // 2026-06-21 is a Sunday, the second day of the 2026-06-20 weekend — the bug this test
        // guards against: the old formula landed on 2026-06-27 (the *following* Saturday)
        // instead of recognizing "now" is already inside the weekend that started yesterday.
        Clock sundayClock = Clock.fixed(Instant.parse("2026-06-21T19:00:00Z"), ZoneId.of("UTC"));
        TimeIntervalResolver resolver = new TimeIntervalResolver(sundayClock, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.WEEKEND, null, zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-20T07:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-22T07:00:00Z"));
    }

    @Test
    void weekendFromMondayImmediatelyAfterIsTheNextWeekendNotTheOneThatJustEnded() {
        // 2026-06-22 is the Monday immediately after the 2026-06-20 weekend — must roll forward
        // to 2026-06-27, not stay on the weekend that just ended.
        Clock mondayAfterClock = Clock.fixed(Instant.parse("2026-06-22T19:00:00Z"), ZoneId.of("UTC"));
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayAfterClock, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.WEEKEND, null, zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-06-27T07:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-06-29T07:00:00Z"));
    }

    @Test
    void specificDateIsThatFullCalendarDay() {
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayNoon, "17:00");
        TimeInterval interval = resolver.resolve(TimeExpressionKind.SPECIFIC_DATE, LocalDate.of(2026, 12, 25), zone);

        assertThat(interval.startAt()).isEqualTo(Instant.parse("2026-12-25T08:00:00Z"));
        assertThat(interval.endAt()).isEqualTo(Instant.parse("2026-12-26T08:00:00Z"));
    }

    @Test
    void specificDateWithoutADateIsRejected() {
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayNoon, "17:00");
        assertThatThrownBy(() -> resolver.resolve(TimeExpressionKind.SPECIFIC_DATE, null, zone))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currentIsNotABoundedIntervalAndIsRejected() {
        TimeIntervalResolver resolver = new TimeIntervalResolver(mondayNoon, "17:00");
        assertThatThrownBy(() -> resolver.resolve(TimeExpressionKind.CURRENT, null, zone))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
