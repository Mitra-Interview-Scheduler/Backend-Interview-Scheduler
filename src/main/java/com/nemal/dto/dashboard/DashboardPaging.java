package com.nemal.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public final class DashboardPaging {

    public static final int PREVIEW_SIZE = 5;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_PREVIEW_SIZE = 20;
    public static final int DEFAULT_UPCOMING_DAYS = 7;

    private DashboardPaging() {}

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
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        if (from >= to) {
            return List.of();
        }
        return List.copyOf(all.subList(from, to));
    }
}
