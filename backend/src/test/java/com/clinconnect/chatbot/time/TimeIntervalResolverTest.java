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
