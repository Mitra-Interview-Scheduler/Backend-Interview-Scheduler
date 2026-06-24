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
        AND (
            :departmentId IS NULL
            OR (
                jsonb_array_length(f.department_ids_json) > 0
                AND f.department_ids_json @> to_jsonb(:departmentId)
            )
        )
        AND (
            :designationId IS NULL
            OR jsonb_array_length(f.designation_ids_json) = 0
            OR f.designation_ids_json @> to_jsonb(:designationId)
        )
        AND (
            :interviewType IS NULL
            OR (
                jsonb_array_length(f.interview_types_json) > 0
                AND f.interview_types_json @> jsonb_build_array(:interviewType)
            )
        )
        """, nativeQuery = true)
    List<FeedbackForm> findActiveFormsByDepartmentDesignationAndInterviewType(
            @Param("departmentId") Long departmentId,
            @Param("designationId") Long designationId,
            @Param("interviewType") String interviewType
    );




}
