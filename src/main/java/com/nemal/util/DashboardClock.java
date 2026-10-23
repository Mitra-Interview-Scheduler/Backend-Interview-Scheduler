package com.nemal.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * One clock for dashboard calculations.
 *
 * All stored times are UTC, so "now", overdue checks and rolling windows ("last 7 days") use
 * UTC. Calendar-day boundaries ("today", "this month") follow the viewer's own timezone and
 * are converted back to UTC before they are compared with stored times.
 */
public final class DashboardClock {

    private DashboardClock() {}

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** The viewer's local calendar date for a UTC instant. */
    public static LocalDate localDate(LocalDateTime utc, ZoneId zone) {
        return TimeZoneMapper.fromUtc(utc, zone).toLocalDate();
    }

    /** Start of the viewer's day {@code plusDays} days after the day containing {@code nowUtc}, in UTC. */
    public static LocalDateTime startOfLocalDayUtc(LocalDateTime nowUtc, ZoneId zone, int plusDays) {
        return TimeZoneMapper.toUtc(localDate(nowUtc, zone).plusDays(plusDays).atStartOfDay(), zone);
    }

    /** Start of a given local date in the viewer's timezone, in UTC. */
    public static LocalDateTime startOfLocalDateUtc(LocalDate localDate, ZoneId zone) {
        return TimeZoneMapper.toUtc(localDate.atStartOfDay(), zone);
    }

    /** Whole calendar days between two UTC instants, counted on the viewer's calendar. */
    public static long localDaysBetween(LocalDateTime fromUtc, LocalDateTime toUtc, ZoneId zone) {
        if (fromUtc == null || toUtc == null) return 0;
        return ChronoUnit.DAYS.between(localDate(fromUtc, zone), localDate(toUtc, zone));
    }
}
