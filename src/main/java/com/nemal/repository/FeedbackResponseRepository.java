package com.nemal.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemal.entity.FeedbackResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedbackResponseRepository extends JpaRepository<FeedbackResponse, Long> {
    Optional<FeedbackResponse> findByInterviewScheduleId(Long interviewScheduleId);
    Optional<FeedbackResponse> findByInterviewScheduleIdAndInterviewerId(Long interviewScheduleId, Long interviewerId);
    boolean existsByInterviewScheduleId(Long interviewScheduleId);
    boolean existsByInterviewScheduleIdAndInterviewerId(Long interviewScheduleId, Long interviewerId);
    boolean existsByFormId(Long formId);

    @Query("""
            SELECT DISTINCT fr.interviewSchedule.id FROM FeedbackResponse fr
            WHERE fr.interviewSchedule.id IN :scheduleIds
            """)
    List<Long> findScheduleIdsWithFeedback(@Param("scheduleIds") Collection<Long> scheduleIds);

    @Query("""
            SELECT fr FROM FeedbackResponse fr
            JOIN FETCH fr.form
            JOIN FETCH fr.interviewer
            JOIN fr.interviewSchedule feedbackSchedule
            JOIN feedbackSchedule.request feedbackRequest
            WHERE feedbackRequest.panel.id = (
                SELECT r.panel.id FROM InterviewRequest r
                WHERE r.interviewSchedule.id = :scheduleId AND r.panel IS NOT NULL
            )
            ORDER BY fr.submittedAt DESC
            """)
    List<FeedbackResponse> findPanelFeedbackForPeerSchedule(@Param("scheduleId") Long scheduleId);

    @Query("""
            SELECT fr FROM FeedbackResponse fr
            JOIN FETCH fr.form
            JOIN FETCH fr.interviewer
            WHERE fr.interviewSchedule.id IN (
                SELECT s.id FROM InterviewSchedule s
                JOIN s.request r
                WHERE r.panel.id = :panelId
            )
            ORDER BY fr.submittedAt DESC
            """)
    List<FeedbackResponse> findByPanelIdOrderBySubmittedAtDesc(@Param("panelId") Long panelId);

    /** Panel feedback for several panels at once: (panelId, response) pairs, newest first. */
    @Query("""
            SELECT r.panel.id, fr FROM FeedbackResponse fr
            JOIN fr.interviewSchedule s
            JOIN s.request r
            JOIN FETCH fr.interviewer
            WHERE r.panel.id IN :panelIds
            ORDER BY fr.submittedAt DESC
            """)
    List<Object[]> findByPanelIdsOrderBySubmittedAtDesc(@Param("panelIds") Collection<Long> panelIds);

    List<FeedbackResponse> findByInterviewerIdOrderBySubmittedAtDesc(Long interviewerId);

    @Query("""
            SELECT COUNT(fr) FROM FeedbackResponse fr
            WHERE fr.submittedAt >= :from AND fr.submittedAt < :to
            """)
    long countSubmittedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT fr.form.id, fr.form.name, fr.form.versionNumber, fr.form.isActive, COUNT(fr)
            FROM FeedbackResponse fr
            GROUP BY fr.form.id, fr.form.name, fr.form.versionNumber, fr.form.isActive
            ORDER BY COUNT(fr) DESC
            """)
    List<Object[]> countByForm();

    /** Median hours from interview end to feedback submission, all interviewers, since {@code from}. */
    @Query(value = """
            SELECT percentile_cont(0.5) WITHIN GROUP (
                       ORDER BY EXTRACT(EPOCH FROM (fr.submitted_at - COALESCE(s.completed_at, s.end_date_time))) / 3600.0)
            FROM feedback_responses fr
            JOIN interview_schedules s ON s.id = fr.interview_schedule_id
            WHERE fr.submitted_at >= :from
              AND COALESCE(s.completed_at, s.end_date_time) IS NOT NULL
              AND fr.submitted_at >= COALESCE(s.completed_at, s.end_date_time)
            """, nativeQuery = true)
    Double medianTurnaroundHoursSince(@Param("from") LocalDateTime from);

    /** Median turnaround hours for one interviewer since {@code from}. */
    @Query(value = """
            SELECT percentile_cont(0.5) WITHIN GROUP (
                       ORDER BY EXTRACT(EPOCH FROM (fr.submitted_at - COALESCE(s.completed_at, s.end_date_time))) / 3600.0)
            FROM feedback_responses fr
            JOIN interview_schedules s ON s.id = fr.interview_schedule_id
            WHERE fr.submitted_at >= :from
              AND fr.interviewer_id = :interviewerId
              AND COALESCE(s.completed_at, s.end_date_time) IS NOT NULL
              AND fr.submitted_at >= COALESCE(s.completed_at, s.end_date_time)
            """, nativeQuery = true)
    Double medianTurnaroundHoursForInterviewerSince(
            @Param("interviewerId") Long interviewerId,
            @Param("from") LocalDateTime from);

    /** Average turnaround hours, all interviewers, since {@code from}. */
    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (fr.submitted_at - COALESCE(s.completed_at, s.end_date_time))) / 3600.0)
            FROM feedback_responses fr
            JOIN interview_schedules s ON s.id = fr.interview_schedule_id
            WHERE fr.submitted_at >= :from
              AND COALESCE(s.completed_at, s.end_date_time) IS NOT NULL
              AND fr.submitted_at >= COALESCE(s.completed_at, s.end_date_time)
            """, nativeQuery = true)
    Double averageTurnaroundHoursSince(@Param("from") LocalDateTime from);

    /** One interviewer's submitted answers since {@code from} (for the recommendation mix). */
    @Query("""
            SELECT fr.responsesJson FROM FeedbackResponse fr
            WHERE fr.interviewer.id = :interviewerId
              AND fr.submittedAt >= :from
            """)
    List<JsonNode> findResponsesJsonByInterviewerSince(
            @Param("interviewerId") Long interviewerId,
            @Param("from") LocalDateTime from);

    /** Panel feedback answers since {@code from}: (panelId, responsesJson). */
    @Query("""
            SELECT p.id, fr.responsesJson FROM FeedbackResponse fr
            JOIN fr.interviewSchedule s
            JOIN s.request r
            JOIN r.panel p
            WHERE fr.submittedAt >= :from
            """)
    List<Object[]> findPanelResponsesJsonSince(@Param("from") LocalDateTime from);
}
