package com.nemal.dto.dashboard;

import com.nemal.util.DashboardClock;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardPagingTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo"); // UTC+05:30

    @Test
    void rangesAreClampedToSupportedValues() {
        assertEquals(90, DashboardPaging.trendDays(90));
        assertEquals(30, DashboardPaging.trendDays(36500));
        assertEquals(30, DashboardPaging.trendDays(null));
        assertEquals(30, DashboardPaging.capacityDays(100000));
        assertEquals(1, DashboardPaging.capacityDays(-5));
        assertEquals(90, DashboardPaging.stalledDays(1000));
        assertEquals(168, DashboardPaging.feedbackOverdueHours(Integer.MAX_VALUE));
    }

    @Test
    void hugePageNumbersAreCappedAndSliceSafely() {
        assertEquals(DashboardPaging.MAX_PAGE, DashboardPaging.page(Integer.MAX_VALUE));
        assertEquals(0, DashboardPaging.page(-3));
        assertTrue(DashboardPaging.slice(List.of(1, 2, 3), Integer.MAX_VALUE, 50).isEmpty());
        assertEquals(List.of(3), DashboardPaging.slice(List.of(1, 2, 3), 1, 2));
    }

    @Test
    void upcomingWindowEndsAtTheViewersLocalMidnight() {
        // 20:00 UTC on Oct 22 is already Oct 23 01:30 in Colombo.
        LocalDateTime nowUtc = LocalDateTime.of(2026, 10, 22, 20, 0);
        // Colombo midnight starting Oct 30 (Oct 23 + 7 days) = Oct 29 18:30 UTC.
        assertEquals(LocalDateTime.of(2026, 10, 29, 18, 30),
                DashboardPaging.upcomingWindowEnd(nowUtc, 7, COLOMBO));
    }

    @Test
    void dayCountsFollowTheViewersCalendar() {
        LocalDateTime nowUtc = LocalDateTime.of(2026, 10, 22, 20, 0);   // Oct 23 locally
        LocalDateTime earlierUtc = LocalDateTime.of(2026, 10, 22, 17, 0); // Oct 22 22:30 locally
        assertEquals(1, DashboardClock.localDaysBetween(earlierUtc, nowUtc, COLOMBO));
        assertEquals(0, DashboardClock.localDaysBetween(earlierUtc, nowUtc, ZoneId.of("UTC")));
        assertEquals(LocalDateTime.of(2026, 10, 22, 18, 30),
                DashboardClock.startOfLocalDayUtc(nowUtc, COLOMBO, 0));
    }
}
