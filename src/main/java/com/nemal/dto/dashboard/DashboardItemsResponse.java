package com.nemal.dto.dashboard;

import java.util.List;

public record DashboardItemsResponse(
        String section,
        int count,
        List<?> items,
        int page,
        int size,
        boolean hasMore
) {
    public static DashboardItemsResponse of(String section, CountedList<?> list) {
        if (list == null) {
            return new DashboardItemsResponse(
                    section, 0, List.of(), 0, DashboardPaging.DEFAULT_PAGE_SIZE, false);
        }
        return new DashboardItemsResponse(
                section, list.count(), list.items(), list.page(), list.size(), list.hasMore());
    }

    public static DashboardItemsResponse of(String section, List<?> all, int page, int size) {
        return of(section, CountedList.page(all, page, size));
    }
}
