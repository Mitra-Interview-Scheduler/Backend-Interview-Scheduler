package com.nemal.dto;

import java.util.List;

public record FeedbackScopesDto(
        List<Long> departmentIds,
        List<Long> designationIds,
        List<String> interviewTypes
) {
}
