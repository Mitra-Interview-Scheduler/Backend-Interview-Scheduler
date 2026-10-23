package com.nemal.repository;

import com.nemal.entity.FeedbackNudgeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedbackNudgeLogRepository extends JpaRepository<FeedbackNudgeLog, Long> {

    long countByInterviewScheduleId(Long interviewScheduleId);

    Optional<FeedbackNudgeLog> findTopByInterviewScheduleIdOrderByCreatedAtDesc(Long interviewScheduleId);

    /** Most recent nudge sent to one interviewer for one schedule (used for the nudge cooldown). */
    Optional<FeedbackNudgeLog> findTopByInterviewScheduleIdAndRecipientIdOrderByCreatedAtDesc(
            Long interviewScheduleId, Long recipientId);

    @Query("""
            SELECT n.interviewSchedule.id, COUNT(n)
            FROM FeedbackNudgeLog n
            WHERE n.interviewSchedule.id IN :scheduleIds
            GROUP BY n.interviewSchedule.id
            """)
    List<Object[]> countByScheduleIds(@Param("scheduleIds") Collection<Long> scheduleIds);

    @Query("""
            SELECT n FROM FeedbackNudgeLog n
            LEFT JOIN FETCH n.nudgedBy
            WHERE n.interviewSchedule.id IN :scheduleIds
            AND n.createdAt = (
                SELECT MAX(n2.createdAt) FROM FeedbackNudgeLog n2
                WHERE n2.interviewSchedule.id = n.interviewSchedule.id
            )
            """)
    List<FeedbackNudgeLog> findLatestByScheduleIds(@Param("scheduleIds") Collection<Long> scheduleIds);
}
