package com.nemal.dto;

public record GoogleCalendarListItemDto(
        String id,
        String name,
        String accessRole,
        boolean primary,
        boolean googleSelected,
        boolean selected
) {}
