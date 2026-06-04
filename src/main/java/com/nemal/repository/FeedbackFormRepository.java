package com.nemal.repository;

import com.nemal.entity.FeedbackForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface FeedbackFormRepository extends JpaRepository<FeedbackForm, Long> {
    Optional<FeedbackForm> findFirstByIsActiveTrueOrderByIdDesc();
    List<FeedbackForm> findAllByIsActiveTrue();
    List<FeedbackForm> findBySeriesKey(String seriesKey);


    /**
     * Finds active feedback forms that are applicable to a given department and designation.
     * A form is applicable if its department/designation list is empty (applies to all)
     * or if it contains the specified ID.
     *
     * @param departmentId The ID of the candidate's department.
     * @param designationId The ID of the candidate's target designation.
     * @return A list of applicable feedback forms.
     */
    @Query(value = """
        SELECT * FROM feedback_forms f
        WHERE f.is_active = true
        AND (jsonb_array_length(f.department_ids_json) = 0 OR f.department_ids_json @> to_jsonb(:departmentId))
        AND (jsonb_array_length(f.designation_ids_json) = 0 OR f.designation_ids_json @> to_jsonb(:designationId))
        """, nativeQuery = true)
    List<FeedbackForm> findActiveFormsByDepartmentAndDesignation(
            @Param("departmentId") Long departmentId,
            @Param("designationId") Long designationId
    );




}
