package com.nemal.dto;

import java.util.List;

/**
 * Resolved interviewer filters for scheduling, aligned with AvailabilityFilterDto /
 * frontend filterData (minTierId carries tierOrder; minDesignationLevel carries levelOrder).
 */
public record ResolvedInterviewerFiltersDto(
        List<Long> departmentIds,
        Long departmentIdForDesignationFilter,
        Long minTierId,
        Long minDesignationLevelInDepartment,
        Integer minYearsOfExperience,
        List<Long> technologyIds,
        List<Long> domainIds
) {}
