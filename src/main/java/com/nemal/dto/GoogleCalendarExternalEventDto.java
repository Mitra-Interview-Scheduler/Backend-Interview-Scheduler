package com.nemal.dto;

import java.time.LocalDateTime;

public record GoogleCalendarExternalEventDto(
        String googleEventId,
        String title,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        boolean allDay,
        boolean readOnly
) {
    public GoogleCalendarExternalEventDto(
            String googleEventId,
            String title,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            boolean allDay) {
        this(googleEventId, title, startDateTime, endDateTime, allDay, true);
    }
}
