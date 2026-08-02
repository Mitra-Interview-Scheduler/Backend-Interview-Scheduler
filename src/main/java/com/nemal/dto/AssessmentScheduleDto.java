package com.nemal.dto;

import com.nemal.enums.AssessmentPhase;

import java.time.LocalDateTime;
import java.util.List;

public record AssessmentScheduleDto(
        Long interviewScheduleId,
        Long interviewRequestId,
        Long candidateId,
        String candidateName,
        String interviewType,
        String interviewTypeLabel,
        AssessmentPhase assessmentPhase,
        boolean hasAssessmentFile,
        String assessmentFileName,
        Long assessmentFileSize,
        LocalDateTime assessmentUploadedAt,
        LocalDateTime dueStartDateTime,
        LocalDateTime dueEndDateTime,
        String notes,
        List<AssessmentReviewerDto> reviewers
) {}
