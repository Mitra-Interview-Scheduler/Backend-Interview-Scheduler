package com.nemal.dto.dashboard;

import java.util.List;

public record CountedList<T>(int count, List<T> items, int page, int size, boolean hasMore) {

    public static <T> CountedList<T> of(List<T> items) {
        List<T> safe = items == null ? List.of() : items;
        return new CountedList<>(safe.size(), safe, 0, Math.max(1, safe.size()), false);
    }

    public static <T> CountedList<T> empty() {
        return new CountedList<>(0, List.of(), 0, DashboardPaging.PREVIEW_SIZE, false);
    }

    public static <T> CountedList<T> page(List<T> all, int page, int size) {
        List<T> source = all == null ? List.of() : all;
        return fromPage(DashboardPaging.slice(source, page, size), source.size(), page, size);
    }

    public static <T> CountedList<T> fromPage(List<T> pageItems, int total, int page, int size) {
        List<T> safe = pageItems == null ? List.of() : pageItems;
        int s = Math.max(1, size);
        int p = Math.max(0, page);
        boolean hasMore = (long) (p + 1) * s < total;
        return new CountedList<>(Math.max(0, total), safe, p, s, hasMore);
    }
}
