package com.nemal.repository;

import com.nemal.entity.InterviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRequestRepository extends JpaRepository<InterviewRequest, Long> {

    @Query("""
            SELECT r.panel.id FROM InterviewRequest r
            WHERE r.interviewSchedule.id = :scheduleId AND r.panel IS NOT NULL
            """)
    Optional<Long> findPanelIdByInterviewScheduleId(@Param("scheduleId") Long scheduleId);

    List<InterviewRequest> findByInterviewScheduleId(Long interviewScheduleId);

    @Query("""
            SELECT r FROM InterviewRequest r
            LEFT JOIN FETCH r.interviewSchedule s
            LEFT JOIN FETCH r.panel
            WHERE s.id = :interviewScheduleId
            """)
    List<InterviewRequest> findByInterviewScheduleIdWithDetails(
            @Param("interviewScheduleId") Long interviewScheduleId);
    List<InterviewRequest> findByRequestedByIdOrderByCreatedAtDesc(Long userId);

    List<InterviewRequest> findByCandidateId(Long candidateId);

    @Query("SELECT DISTINCT r FROM InterviewRequest r " +
            "LEFT JOIN FETCH r.interviewSchedule " +
            "LEFT JOIN FETCH r.assignedInterviewer ai " +
            "LEFT JOIN FETCH ai.currentDesignation " +
            "LEFT JOIN FETCH r.candidate " +
            "WHERE r.candidate.id = :candidateId " +
            "ORDER BY r.createdAt DESC")
    List<InterviewRequest> findByCandidateIdWithSchedule(@Param("candidateId") Long candidateId);

    List<InterviewRequest> findByAssignedInterviewerId(Long interviewerId);

    /**
     * Upcoming interviews for an interviewer — excludes CANCELLED so the
     * interviewer dashboard immediately reflects cancellations.
     */
    @Query("SELECT r FROM InterviewRequest r " +
            "WHERE r.assignedInterviewer.id = :interviewerId " +
            "AND r.preferredStartDateTime > :now " +
            "AND r.status = 'ACCEPTED' " +
            "ORDER BY r.preferredStartDateTime ASC")
    List<InterviewRequest> findUpcomingInterviewsForInterviewer(
            @Param("interviewerId") Long interviewerId,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT r FROM InterviewRequest r " +
            "WHERE r.assignedInterviewer.id = :interviewerId " +
            "AND r.preferredStartDateTime > :now " +
            "AND r.status = 'ACCEPTED' " +
            "ORDER BY r.preferredStartDateTime ASC")
    List<InterviewRequest> findUpcomingInterviewsForInterviewer(
            @Param("interviewerId") Long interviewerId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("SELECT DISTINCT r FROM InterviewRequest r " +
            "LEFT JOIN FETCH r.candidate c " +
            "LEFT JOIN FETCH c.coordinatedHr " +
            "LEFT JOIN FETCH r.interviewCoordinator " +
            "LEFT JOIN c.targetDesignation cd " +
            "LEFT JOIN r.candidateDesignation rd " +
            "LEFT JOIN cd.tier ct " +
            "LEFT JOIN rd.tier rt " +
            "WHERE r.requestedBy.id = :userId " +
            "AND (:departmentId IS NULL OR (c.department.id = :departmentId) OR (rd.department.id = :departmentId)) " +
            "AND (:minTierOrder IS NULL OR ( (ct.tierOrder IS NOT NULL AND ct.tierOrder >= :minTierOrder) OR (rt.tierOrder IS NOT NULL AND rt.tierOrder >= :minTierOrder) )) " +
            "AND (:exactTierOrder IS NULL OR ( (ct.tierOrder IS NOT NULL AND ct.tierOrder = :exactTierOrder) OR (rt.tierOrder IS NOT NULL AND rt.tierOrder = :exactTierOrder) )) " +
            "ORDER BY r.createdAt DESC")
    List<InterviewRequest> findByRequestedByIdWithFilters(
            @Param("userId") Long userId,
            @Param("departmentId") Long departmentId,
            @Param("minTierOrder") Integer minTierOrder,
            @Param("exactTierOrder") Integer exactTierOrder
    );

    @Query("""
            SELECT r FROM InterviewRequest r
            JOIN FETCH r.assignedInterviewer
            WHERE r.status = 'ACCEPTED'
              AND r.preferredStartDateTime > :now
              AND r.preferredStartDateTime <= :windowEnd
            """)
    List<InterviewRequest> findAcceptedInterviewsStartingBetween(
            @Param("now") LocalDateTime now,
            @Param("windowEnd") LocalDateTime windowEnd
    );
}