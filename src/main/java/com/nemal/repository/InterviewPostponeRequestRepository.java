package com.nemal.repository;

import com.nemal.entity.InterviewPostponeRequest;
import com.nemal.enums.PostponeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewPostponeRequestRepository extends JpaRepository<InterviewPostponeRequest, Long> {

    Optional<InterviewPostponeRequest> findByInterviewScheduleIdAndStatus(
            Long interviewScheduleId,
            PostponeRequestStatus status);

    List<InterviewPostponeRequest> findByInterviewScheduleIdInAndStatus(
            Collection<Long> interviewScheduleIds,
            PostponeRequestStatus status);

    List<InterviewPostponeRequest> findByInterviewScheduleIdOrderByCreatedAtDesc(Long interviewScheduleId);

    List<InterviewPostponeRequest> findByStatusOrderByCreatedAtDesc(PostponeRequestStatus status);

    long countByStatus(PostponeRequestStatus status);

    @Query("""
            SELECT DISTINCT p FROM InterviewPostponeRequest p
            LEFT JOIN FETCH p.interviewSchedule sch
            LEFT JOIN FETCH sch.interviewer
            LEFT JOIN FETCH sch.request schReq
            LEFT JOIN FETCH p.interviewRequest req
            LEFT JOIN FETCH req.candidate cand
            LEFT JOIN FETCH cand.coordinatedHr
            LEFT JOIN FETCH req.candidateDesignation
            LEFT JOIN FETCH req.assignedInterviewer
            LEFT JOIN FETCH req.interviewCoordinator
            LEFT JOIN FETCH req.panel panel
            LEFT JOIN FETCH panel.interviewCoordinator
            LEFT JOIN FETCH req.requiredTechnologies
            LEFT JOIN FETCH p.requestedBy
            LEFT JOIN FETCH p.reviewedBy
            WHERE p.id = :id
            """)
    Optional<InterviewPostponeRequest> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT p FROM InterviewPostponeRequest p
            LEFT JOIN FETCH p.interviewSchedule sch
            LEFT JOIN FETCH sch.interviewer
            LEFT JOIN FETCH sch.request schReq
            LEFT JOIN FETCH p.interviewRequest req
            LEFT JOIN FETCH req.candidate cand
            LEFT JOIN FETCH cand.coordinatedHr
            LEFT JOIN FETCH req.candidateDesignation
            LEFT JOIN FETCH req.assignedInterviewer
            LEFT JOIN FETCH req.interviewCoordinator
            LEFT JOIN FETCH req.panel panel
            LEFT JOIN FETCH panel.interviewCoordinator
            LEFT JOIN FETCH req.requiredTechnologies
            LEFT JOIN FETCH p.requestedBy
            LEFT JOIN FETCH p.reviewedBy
            WHERE p.interviewSchedule.id = :scheduleId
            AND p.status = :status
            """)
    Optional<InterviewPostponeRequest> findPendingByScheduleIdWithDetails(
            @Param("scheduleId") Long scheduleId,
            @Param("status") PostponeRequestStatus status);
}
