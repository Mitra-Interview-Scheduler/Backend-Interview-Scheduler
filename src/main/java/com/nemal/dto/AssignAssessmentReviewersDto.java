package com.nemal.dto;

import java.util.List;

public record AssignAssessmentReviewersDto(
        List<Long> reviewerUserIds
) {}
