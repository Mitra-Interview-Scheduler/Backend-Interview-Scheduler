package com.nemal.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public final class DashboardPaging {

    public static final int PREVIEW_SIZE = 5;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_PREVIEW_SIZE = 20;
    public static final int DEFAULT_UPCOMING_DAYS = 7;
    public static final int DEFAULT_TREND_DAYS = 30;
    public static final int DEFAULT_CAPACITY_DAYS = 14;
    public static final int MAX_CAPACITY_DAYS = 30;
    public static final int DEFAULT_STALLED_DAYS = 7;
    public static final int MAX_STALLED_DAYS = 90;
    public static final int DEFAULT_FEEDBACK_OVERDUE_HOURS = 24;
    public static final int MAX_FEEDBACK_OVERDUE_HOURS = 168;

    private DashboardPaging() {}

    /** Trend window in days. Allowed values: 7, 14, 30, 90 (anything else falls back to 30). */
    public static int trendDays(Integer days) {
        if (days != null && (days == 7 || days == 14 || days == 30 || days == 90)) {
            return days;
        }
        return DEFAULT_TREND_DAYS;
    }

    /** Capacity look-ahead in days, 1..30. */
    public static int capacityDays(Integer days) {
        return clamp(days, DEFAULT_CAPACITY_DAYS, 1, MAX_CAPACITY_DAYS);
    }

    /** Days without a pipeline change before a candidate counts as stalled, 1..90. */
    public static int stalledDays(Integer days) {
        return clamp(days, DEFAULT_STALLED_DAYS, 1, MAX_STALLED_DAYS);
    }

    /** Hours after an interview ends before missing feedback counts as overdue, 1..168. */
    public static int feedbackOverdueHours(Integer hours) {
        return clamp(hours, DEFAULT_FEEDBACK_OVERDUE_HOURS, 1, MAX_FEEDBACK_OVERDUE_HOURS);
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        int v = value == null ? fallback : value;
        return Math.min(max, Math.max(min, v));
    }

    public static int page(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    public static int size(Integer size) {
        int value = size == null ? DEFAULT_PAGE_SIZE : size;
        return Math.min(MAX_PAGE_SIZE, Math.max(1, value));
    }

    public static int previewSize(Integer size) {
        int value = size == null ? PREVIEW_SIZE : size;
        return Math.min(MAX_PREVIEW_SIZE, Math.max(1, value));
    }

    /** Calendar days (weekends included). Allowed values: 7, 14, 30. */
    public static int upcomingDays(Integer days) {
        if (days != null && (days == 14 || days == 30)) {
            return days;
        }
        return DEFAULT_UPCOMING_DAYS;
    }

    /** Exclusive end of the upcoming window: start of today + N calendar days. */
    public static LocalDateTime upcomingWindowEnd(LocalDateTime now, int days) {
        LocalDateTime moment = now == null ? LocalDateTime.now() : now;
        return moment.toLocalDate().plusDays(upcomingDays(days)).atStartOfDay();
    }

    public static <T> List<T> slice(List<T> all, int page, int size) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        // long math: a huge page number must not overflow into a negative index
        long start = (long) Math.max(0, page) * Math.max(1, size);
        if (start >= all.size()) {
            return List.of();
        }
        int from = (int) start;
        int to = Math.min(from + Math.max(1, size), all.size());
        if (from >= to) {
            return List.of();
        }
        return List.copyOf(all.subList(from, to));
    }
}
