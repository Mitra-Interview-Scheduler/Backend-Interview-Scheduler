package com.nemal.dto;

public record GoogleCalendarStatusDto(
        boolean connected,
        String googleAccountEmail,
        boolean required
) {}
