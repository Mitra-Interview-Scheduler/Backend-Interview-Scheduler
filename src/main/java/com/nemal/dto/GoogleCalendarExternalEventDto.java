package com.nemal.dto;

import java.time.LocalDateTime;

public record GoogleCalendarExternalEventDto(
        String googleEventId,
        String title,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        boolean allDay,
        boolean readOnly,
        String calendarName
) {
    public GoogleCalendarExternalEventDto(
            String googleEventId,
            String title,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            boolean allDay) {
        this(googleEventId, title, startDateTime, endDateTime, allDay, true, null);
    }

    public GoogleCalendarExternalEventDto(
            String googleEventId,
            String title,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            boolean allDay,
            boolean readOnly) {
        this(googleEventId, title, startDateTime, endDateTime, allDay, readOnly, null);
    }
}
