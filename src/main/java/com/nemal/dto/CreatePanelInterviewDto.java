package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record CreatePanelInterviewDto(
        Long candidateId,
        String candidateName,
        Long candidateDesignationId,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startDateTime,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endDateTime,

        List<Long> availabilitySlotIds,
        List<Long> requiredTechnologyIds,
        boolean isUrgent,
        String notes,
        String interviewType,
        Long interviewCoordinatorId,
        Long interviewCoordinatorDepartmentId
) {}