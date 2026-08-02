package com.nemal.repository;

import com.nemal.entity.AssessmentReviewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssessmentReviewerRepository extends JpaRepository<AssessmentReviewer, Long> {

    List<AssessmentReviewer> findByInterviewScheduleIdOrderByAssignedAtAsc(Long interviewScheduleId);

    Optional<AssessmentReviewer> findByInterviewScheduleIdAndReviewerId(Long interviewScheduleId, Long reviewerId);

    boolean existsByInterviewScheduleIdAndReviewerId(Long interviewScheduleId, Long reviewerId);

    void deleteByInterviewScheduleId(Long interviewScheduleId);

    @Query("""
            SELECT ar FROM AssessmentReviewer ar
            JOIN FETCH ar.interviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            WHERE ar.reviewer.id = :reviewerId
            ORDER BY ar.assignedAt DESC
            """)
    List<AssessmentReviewer> findByReviewerIdWithSchedule(@Param("reviewerId") Long reviewerId);
}
