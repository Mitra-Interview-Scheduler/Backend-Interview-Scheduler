package com.nemal.dto.dashboard;

public record DashboardActionResponse(boolean success, String message) {
    public static DashboardActionResponse ok(String message) {
        return new DashboardActionResponse(true, message);
    }
}
