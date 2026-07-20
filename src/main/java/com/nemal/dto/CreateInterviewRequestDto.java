package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record CreateInterviewRequestDto(
        Long candidateId,
        String candidateName,
        String candidateEmail,
        Long candidateDesignationId,
        List<Long> requiredTechnologyIds,
        Long availabilitySlotId,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime preferredStartDateTime,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime preferredEndDateTime,

        boolean isUrgent,
        String notes,
        String interviewType,
        Long interviewCoordinatorId,
        Long interviewCoordinatorDepartmentId,
        Boolean acknowledgeCalendarConflict
) {}