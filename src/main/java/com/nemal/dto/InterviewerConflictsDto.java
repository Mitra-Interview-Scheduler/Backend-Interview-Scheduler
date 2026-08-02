package com.nemal.dto;

import java.util.List;

/**
 * The conflicting Google Calendar events found for a single interviewer within
 * a proposed interview window. Only interviewers with at least one conflict are
 * returned by the conflict-check endpoint.
 */
public record InterviewerConflictsDto(
        Long interviewerId,
        String interviewerName,
        List<GoogleCalendarExternalEventDto> conflicts
) {}
