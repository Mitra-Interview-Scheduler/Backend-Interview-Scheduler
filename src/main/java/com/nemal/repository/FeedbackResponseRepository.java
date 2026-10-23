package com.nemal.repository;

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
            SELECT fr FROM FeedbackResponse fr
            JOIN FETCH fr.form
            JOIN FETCH fr.interviewSchedule s
            JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate
            WHERE fr.interviewer.id = :interviewerId
              AND fr.submittedAt >= :from
            ORDER BY fr.submittedAt DESC
            """)
    List<FeedbackResponse> findByInterviewerSince(
            @Param("interviewerId") Long interviewerId,
            @Param("from") LocalDateTime from);

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

    @Query("""
            SELECT fr FROM FeedbackResponse fr
            JOIN FETCH fr.interviewSchedule s
            WHERE fr.submittedAt >= :from
            """)
    List<FeedbackResponse> findAllSubmittedSince(@Param("from") LocalDateTime from);
}
