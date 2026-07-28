package com.nemal.dto;

public record ApproveInterviewPostponeRequestDto(
        String reviewNotes,
        Boolean acknowledgeCalendarConflict
) {}
