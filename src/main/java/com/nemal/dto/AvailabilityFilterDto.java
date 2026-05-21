// AvailabilityFilterDto.java
package com.nemal.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AvailabilityFilterDto(
        List<Long> departmentIds,
        List<Long> technologyIds,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        Integer minYearsOfExperience,
        Long minDesignationLevelInDepartment,  // NEW: Filter by minimum designation level
        Long departmentIdForDesignationFilter, // NEW: Department for designation filtering
        Long minTierId,                        // NEW: Filter by minimum tier
        Integer page,
        Integer size
) {}