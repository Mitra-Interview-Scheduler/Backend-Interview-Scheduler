package com.nemal.repository;

import com.nemal.entity.AssessmentReviewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentReviewerRepository extends JpaRepository<AssessmentReviewer, Long> {

    List<AssessmentReviewer> findByInterviewScheduleIdOrderByAssignedAtAsc(Long interviewScheduleId);

    Optional<AssessmentReviewer> findByInterviewScheduleIdAndReviewerId(Long interviewScheduleId, Long reviewerId);

    boolean existsByInterviewScheduleIdAndReviewerId(Long interviewScheduleId, Long reviewerId);

    void deleteByInterviewScheduleId(Long interviewScheduleId);

    void deleteByInterviewScheduleIdAndReviewerId(Long interviewScheduleId, Long reviewerId);

    @Query("""
            SELECT ar FROM AssessmentReviewer ar
            JOIN FETCH ar.interviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            WHERE ar.reviewer.id = :reviewerId
            ORDER BY ar.assignedAt DESC
            """)
    List<AssessmentReviewer> findByReviewerIdWithSchedule(@Param("reviewerId") Long reviewerId);

    /** Reviewer counts for several assessment schedules: (scheduleId, count). */
    @Query("""
            SELECT ar.interviewSchedule.id, COUNT(ar) FROM AssessmentReviewer ar
            WHERE ar.interviewSchedule.id IN :scheduleIds
            GROUP BY ar.interviewSchedule.id
            """)
    List<Object[]> countByScheduleIds(@Param("scheduleIds") Collection<Long> scheduleIds);

    /** Schedules (of the given ones) where at least one assigned reviewer has not submitted feedback. */
    @Query("""
            SELECT DISTINCT ar.interviewSchedule.id FROM AssessmentReviewer ar
            WHERE ar.interviewSchedule.id IN :scheduleIds
              AND NOT EXISTS (
                    SELECT 1 FROM FeedbackResponse fr
                    WHERE fr.interviewSchedule.id = ar.interviewSchedule.id
                      AND fr.interviewer.id = ar.reviewer.id
              )
            """)
    List<Long> findScheduleIdsWithMissingReviews(@Param("scheduleIds") Collection<Long> scheduleIds);

    /** A reviewer's open assignments: received/under review and not yet reviewed by them. */
    @Query("""
            SELECT ar FROM AssessmentReviewer ar
            JOIN FETCH ar.interviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            WHERE ar.reviewer.id = :reviewerId
              AND s.assessmentPhase IN (com.nemal.enums.AssessmentPhase.RECEIVED,
                                        com.nemal.enums.AssessmentPhase.UNDER_REVIEW)
              AND NOT EXISTS (
                    SELECT 1 FROM FeedbackResponse fr
                    WHERE fr.interviewSchedule.id = s.id AND fr.interviewer.id = :reviewerId
              )
            ORDER BY s.endDateTime ASC, s.id ASC
            """)
    List<AssessmentReviewer> findOpenAssignmentsForReviewer(@Param("reviewerId") Long reviewerId);
}
