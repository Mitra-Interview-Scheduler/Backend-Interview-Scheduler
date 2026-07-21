package com.nemal.repository;

import com.nemal.entity.InterviewSchedule;
import com.nemal.enums.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}