package com.nemal.repository;

import com.nemal.entity.InterviewSchedule;
import com.nemal.enums.AssessmentPhase;
import com.nemal.enums.InterviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, Long> {

    /**
     * Find schedules linked to a specific InterviewRequest.
     * Multiple rows can exist historically; callers should prefer {@link #findActiveByRequestId(Long)}.
     */
    List<InterviewSchedule> findByRequestId(Long requestId);

    Optional<InterviewSchedule> findTopByRequestIdAndStatusNotOrderByIdDesc(
            Long requestId,
            InterviewStatus status);

    default Optional<InterviewSchedule> findActiveByRequestId(Long requestId) {
        Optional<InterviewSchedule> active = findTopByRequestIdAndStatusNotOrderByIdDesc(
                requestId, InterviewStatus.CANCELLED);
        if (active.isPresent()) {
            return active;
        }
        return findByRequestId(requestId).stream().findFirst();
    }

    @Query("""
            SELECT s FROM InterviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.panel
            WHERE s.id = :scheduleId
            """)
    Optional<InterviewSchedule> findByIdWithRequestAndPanel(@Param("scheduleId") Long scheduleId);

    @Query("""
            SELECT s FROM InterviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            LEFT JOIN FETCH c.coordinatedHr
            LEFT JOIN FETCH c.targetDesignation
            LEFT JOIN FETCH r.candidateDesignation
            LEFT JOIN FETCH r.assignedInterviewer
            LEFT JOIN FETCH r.requestedBy
            LEFT JOIN FETCH r.interviewCoordinator
            LEFT JOIN FETCH r.panel p
            LEFT JOIN FETCH p.interviewCoordinator
            LEFT JOIN FETCH s.interviewer
            WHERE s.id = :scheduleId
            """)
    Optional<InterviewSchedule> findByIdWithPostponeDetails(@Param("scheduleId") Long scheduleId);

    @Query("""
            SELECT s FROM InterviewSchedule s
            JOIN s.request r
            WHERE r.panel.id = :panelId
            """)
    List<InterviewSchedule> findByPanelId(@Param("panelId") Long panelId);

    /** Whether any schedule references the given interview type code (used to soft-delete types in use). */
    boolean existsByInterviewTypeIgnoreCase(String interviewType);

    long countByInterviewTypeIgnoreCase(String interviewType);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM InterviewSchedule s
            WHERE s.request.candidate.id = :candidateId
              AND s.interviewer.id = :interviewerId
              AND s.status <> com.nemal.enums.InterviewStatus.CANCELLED
            """)
    boolean existsActiveScheduleForCandidate(
            @Param("candidateId") Long candidateId,
            @Param("interviewerId") Long interviewerId);

    @Query("""
            SELECT DISTINCT s FROM InterviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            LEFT JOIN FETCH c.coordinatedHr
            LEFT JOIN FETCH c.department
            LEFT JOIN FETCH s.interviewer
            LEFT JOIN FETCH r.panel
            WHERE s.status <> com.nemal.enums.InterviewStatus.CANCELLED
              AND s.startDateTime >= :from
              AND s.startDateTime < :to
            ORDER BY s.startDateTime
            """)
    List<InterviewSchedule> findUpcomingInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT DISTINCT s FROM InterviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            LEFT JOIN FETCH c.coordinatedHr
            LEFT JOIN FETCH s.interviewer
            LEFT JOIN FETCH r.panel p
            WHERE s.status <> com.nemal.enums.InterviewStatus.CANCELLED
              AND s.startDateTime >= :from
              AND s.startDateTime < :to
              AND (s.interviewer.id = :interviewerId
                   OR r.assignedInterviewer.id = :interviewerId
                   OR EXISTS (
                        SELECT 1 FROM InterviewRequest pr
                        WHERE pr.panel = p AND pr.assignedInterviewer.id = :interviewerId
                          AND pr.status <> com.nemal.enums.RequestStatus.CANCELLED
                          AND pr.status <> com.nemal.enums.RequestStatus.REJECTED
                   ))
            ORDER BY s.startDateTime
            """)
    List<InterviewSchedule> findUpcomingForInterviewer(
            @Param("interviewerId") Long interviewerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Interviews still missing feedback that ended (or were completed) before {@code cutoff}:
     * not cancelled, not assessments. Optionally scoped to one interviewer and/or one
     * coordinating HR, oldest first. Paged in SQL.
     */
    @Query("""
            SELECT s FROM InterviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            LEFT JOIN FETCH c.coordinatedHr
            LEFT JOIN FETCH s.interviewer i
            WHERE s.assessmentPhase IS NULL
              AND s.status IN (com.nemal.enums.InterviewStatus.COMPLETED, com.nemal.enums.InterviewStatus.SCHEDULED)
              AND COALESCE(s.completedAt, s.endDateTime) < :cutoff
              AND NOT EXISTS (
                    SELECT 1 FROM FeedbackResponse fr WHERE fr.interviewSchedule.id = s.id
              )
              AND (:interviewerId IS NULL OR i.id = :interviewerId)
              AND (:hrId IS NULL OR c.coordinatedHr.id = :hrId)
            ORDER BY COALESCE(s.completedAt, s.endDateTime) ASC, s.id ASC
            """)
    List<InterviewSchedule> findFeedbackOverdue(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("interviewerId") Long interviewerId,
            @Param("hrId") Long hrId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(s) FROM InterviewSchedule s
            LEFT JOIN s.request r
            LEFT JOIN r.candidate c
            WHERE s.assessmentPhase IS NULL
              AND s.status IN (com.nemal.enums.InterviewStatus.COMPLETED, com.nemal.enums.InterviewStatus.SCHEDULED)
              AND COALESCE(s.completedAt, s.endDateTime) < :cutoff
              AND NOT EXISTS (
                    SELECT 1 FROM FeedbackResponse fr WHERE fr.interviewSchedule.id = s.id
              )
              AND (:interviewerId IS NULL OR s.interviewer.id = :interviewerId)
              AND (:hrId IS NULL OR c.coordinatedHr.id = :hrId)
            """)
    long countFeedbackOverdue(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("interviewerId") Long interviewerId,
            @Param("hrId") Long hrId);

    @Query("""
            SELECT DISTINCT s FROM InterviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.candidate c
            LEFT JOIN FETCH c.coordinatedHr
            LEFT JOIN FETCH s.interviewer
            WHERE s.assessmentPhase IN :phases
              AND s.status <> com.nemal.enums.InterviewStatus.CANCELLED
            ORDER BY s.endDateTime ASC, s.id
            """)
    List<InterviewSchedule> findByAssessmentPhases(
            @Param("phases") Collection<AssessmentPhase> phases);

    @Query("""
            SELECT COUNT(s) FROM InterviewSchedule s
            WHERE s.status <> com.nemal.enums.InterviewStatus.CANCELLED
              AND s.startDateTime >= :from
              AND s.startDateTime < :to
            """)
    long countScheduledBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(s) FROM InterviewSchedule s
            WHERE s.status = com.nemal.enums.InterviewStatus.COMPLETED
              AND s.interviewer.id = :interviewerId
              AND COALESCE(s.completedAt, s.endDateTime) >= :from
              AND COALESCE(s.completedAt, s.endDateTime) < :to
            """)
    long countCompletedByInterviewerBetween(
            @Param("interviewerId") Long interviewerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT s.interviewType, COUNT(s) FROM InterviewSchedule s
            WHERE s.status = com.nemal.enums.InterviewStatus.COMPLETED
              AND s.interviewer.id = :interviewerId
              AND COALESCE(s.completedAt, s.endDateTime) >= :from
            GROUP BY s.interviewType
            """)
    List<Object[]> countCompletedByTypeForInterviewer(
            @Param("interviewerId") Long interviewerId,
            @Param("from") LocalDateTime from);

    @Query("""
            SELECT s.interviewer.id, CONCAT(s.interviewer.firstName, ' ', s.interviewer.lastName), COUNT(s)
            FROM InterviewSchedule s
            WHERE s.status <> com.nemal.enums.InterviewStatus.CANCELLED
              AND s.startDateTime >= :from
              AND s.interviewer IS NOT NULL
            GROUP BY s.interviewer.id, s.interviewer.firstName, s.interviewer.lastName
            """)
    List<Object[]> countInterviewsByInterviewerSince(@Param("from") LocalDateTime from);
}