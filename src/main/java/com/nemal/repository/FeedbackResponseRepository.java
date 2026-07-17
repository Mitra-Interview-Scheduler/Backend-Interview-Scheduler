package com.nemal.repository;

import com.nemal.entity.FeedbackResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackResponseRepository extends JpaRepository<FeedbackResponse, Long> {
    Optional<FeedbackResponse> findByInterviewScheduleId(Long interviewScheduleId);
    Optional<FeedbackResponse> findByInterviewScheduleIdAndInterviewerId(Long interviewScheduleId, Long interviewerId);
    boolean existsByInterviewScheduleId(Long interviewScheduleId);
    boolean existsByFormId(Long formId);

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
}
