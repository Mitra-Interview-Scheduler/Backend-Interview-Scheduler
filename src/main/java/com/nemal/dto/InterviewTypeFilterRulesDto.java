package com.nemal.dto;

import com.nemal.enums.InterviewerFilterMode;

import java.util.List;

/** Interviewer matching rules configured on an interview type. */
public record InterviewTypeFilterRulesDto(
        InterviewerFilterMode departmentFilterMode,
        Long fixedDepartmentId,
        Integer minYearsExperience,
        InterviewerFilterMode tierFilterMode,
        Long fixedMinTierId,
        InterviewerFilterMode designationFilterMode,
        Long fixedMinDesignationId,
        InterviewerFilterMode domainFilterMode,
        List<Long> fixedDomainIds,
        InterviewerFilterMode categoryFilterMode,
        List<Long> fixedCategoryIds,
        InterviewerFilterMode technologyFilterMode,
        List<Long> fixedTechnologyIds
) {
    public static InterviewTypeFilterRulesDto defaults() {
        return new InterviewTypeFilterRulesDto(
                InterviewerFilterMode.SAME_AS_CANDIDATE,
                null,
                null,
                InterviewerFilterMode.SAME_AS_CANDIDATE,
                null,
                InterviewerFilterMode.SAME_AS_CANDIDATE,
                null,
                InterviewerFilterMode.SAME_AS_CANDIDATE,
                List.of(),
                InterviewerFilterMode.NONE,
                List.of(),
                InterviewerFilterMode.SAME_AS_CANDIDATE,
                List.of()
        );
    }
}
