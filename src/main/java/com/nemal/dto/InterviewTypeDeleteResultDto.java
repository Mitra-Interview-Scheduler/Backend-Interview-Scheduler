package com.nemal.dto;

public record InterviewTypeDeleteResultDto(
        String action,
        String label
) {
    public static final String ACTION_DEACTIVATED = "DEACTIVATED";
    public static final String ACTION_DELETED = "DELETED";
}
