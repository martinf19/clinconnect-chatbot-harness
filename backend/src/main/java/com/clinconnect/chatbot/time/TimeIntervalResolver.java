package com.clinconnect.chatbot.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts a semantic time expression (from Python interpretation) plus a
 * resolved location timezone into an executable [start, end) instant range,
 * using the trusted server Clock — never a model-supplied timestamp
 * (FR-005; docs/09-SECURITY.md "Spring must not execute... from model
 * output").
 *
 * <p>The precise boundary conventions below (calendar-day TODAY/TOMORROW,
 * TONIGHT starting at a configurable local time, WEEKEND as Saturday
 * 00:00-Monday 00:00) are a POC assumption, not sourced from any documented
 * spec: docs/02-REQUIREMENTS.md FR-005 points to docs/08-API-CONTRACTS.md
 * for "time interval rules," but that document only lists the
 * time_expression.kind enum and defines no boundary semantics — and its own
 * "Remaining Business Questions" #8 leaves the tonight/weekend boundary
 * open. These conventions are deliberately isolated here, behind one
 * component, so a future business-confirmed definition is a local change.
 */
@Component
public class TimeIntervalResolver {

    private final Clock clock;
    private final LocalTime tonightStartLocal;

    public TimeIntervalResolver(
            Clock clock,
            @Value("${clinconnect.time.tonight-start-local}") String tonightStartLocal) {
        this.clock = clock;
        this.tonightStartLocal = LocalTime.parse(tonightStartLocal);
    }

    public TimeInterval resolve(TimeExpressionKind kind, LocalDate specificDate, ZoneId locationZone) {
        ZonedDateTime nowInZone = ZonedDateTime.now(clock.withZone(locationZone));
        LocalDate today = nowInZone.toLocalDate();

        return switch (kind) {
            case TODAY -> calendarDay(today, locationZone);
            case TONIGHT -> new TimeInterval(
                    today.atTime(tonightStartLocal).atZone(locationZone).toInstant(),
                    today.plusDays(1).atStartOfDay(locationZone).toInstant());
            case TOMORROW -> calendarDay(today.plusDays(1), locationZone);
            case WEEKEND -> weekend(today, locationZone);
            case SPECIFIC_DATE -> {
                if (specificDate == null) {
                    throw new IllegalArgumentException("SPECIFIC_DATE requires a specificDate");
                }
                yield calendarDay(specificDate, locationZone);
            }
            case CURRENT -> throw new IllegalArgumentException(
                    "CURRENT is not a bounded interval; use the reference instant directly (get_oncall_now)");
        };
    }

    private TimeInterval calendarDay(LocalDate date, ZoneId zone) {
        return new TimeInterval(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * WEEKEND is still Saturday 00:00 to Monday 00:00 in the location zone (unchanged POC
     * definition) — this only fixes which Saturday anchors that window. The forward-count
     * formula below correctly lands on today when today is already Saturday, and on the
     * upcoming Saturday for any weekday Monday-Friday, but a Sunday is the one day it can't
     * express going "backward" one day to (it would instead skip forward a full 6 days to
     * next Saturday). Sunday is the last day of the weekend that started the day before, not
     * the first day of a week-away one, so it is special-cased to look back one day instead
     * (found via docs/10-EVALUATION-PLAN.md Phase 6 evaluation; planning/PHASE-STATUS.md Phase
     * 6 "weekend_normalization_when_sunday" — no existing unit test exercised a Sunday anchor
     * before that).
     */
    private TimeInterval weekend(LocalDate today, ZoneId zone) {
        LocalDate saturday = today.getDayOfWeek() == DayOfWeek.SUNDAY
                ? today.minusDays(1)
                : today.plusDays((DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7);
        return new TimeInterval(
                saturday.atStartOfDay(zone).toInstant(),
                saturday.plusDays(2).atStartOfDay(zone).toInstant());
    }
}
