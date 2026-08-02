package com.nemal.dto;

import java.time.LocalDateTime;

public record AssessmentReviewerDto(
        Long id,
        Long reviewerUserId,
        String reviewerName,
        String reviewerEmail,
        String designationName,
        String departmentName,
        LocalDateTime assignedAt,
        boolean feedbackSubmitted
) {}
