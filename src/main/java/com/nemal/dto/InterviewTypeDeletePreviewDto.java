package com.nemal.dto;

public record InterviewTypeDeletePreviewDto(
        Long id,
        String label,
        boolean inUse,
        long scheduleCount
) {}
