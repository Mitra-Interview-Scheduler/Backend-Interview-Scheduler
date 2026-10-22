package com.nemal.repository;

import com.nemal.entity.InterviewPostponeRequest;
import com.nemal.enums.PostponeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Query("""
            SELECT DISTINCT p FROM InterviewPostponeRequest p
            LEFT JOIN FETCH p.interviewSchedule sch
            LEFT JOIN FETCH sch.interviewer
            LEFT JOIN FETCH sch.request schReq
            LEFT JOIN FETCH schReq.panel
            LEFT JOIN FETCH p.interviewRequest req
            LEFT JOIN FETCH req.candidate cand
            LEFT JOIN FETCH cand.coordinatedHr
            LEFT JOIN FETCH req.assignedInterviewer
            LEFT JOIN FETCH req.panel panel
            LEFT JOIN FETCH p.requestedBy
            WHERE p.status = :status
            ORDER BY p.createdAt DESC
            """)
    List<InterviewPostponeRequest> findPendingWithDetails(@Param("status") PostponeRequestStatus status);

    @Query("""
            SELECT DISTINCT p FROM InterviewPostponeRequest p
            LEFT JOIN FETCH p.interviewSchedule sch
            LEFT JOIN FETCH sch.request schReq
            LEFT JOIN FETCH schReq.candidate
            LEFT JOIN FETCH p.interviewRequest req
            LEFT JOIN FETCH req.candidate
            LEFT JOIN FETCH p.requestedBy
            LEFT JOIN FETCH p.reviewedBy
            WHERE p.requestedBy.id = :userId
            ORDER BY p.createdAt DESC
            """)
    List<InterviewPostponeRequest> findByRequestedByIdWithDetails(@Param("userId") Long userId);

    long countByRequestedById(Long requestedById);

    @Query("""
            SELECT COUNT(p) FROM InterviewPostponeRequest p
            WHERE p.createdAt >= :from
            """)
    long countCreatedSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT p.requestedBy.id, CONCAT(p.requestedBy.firstName, ' ', p.requestedBy.lastName), COUNT(p)
            FROM InterviewPostponeRequest p
            WHERE p.createdAt >= :from AND p.requestedBy IS NOT NULL
            GROUP BY p.requestedBy.id, p.requestedBy.firstName, p.requestedBy.lastName
            """)
    List<Object[]> countByRequesterSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT COALESCE(c.id, rc.id), COALESCE(c.name, rc.name), COUNT(p)
            FROM InterviewPostponeRequest p
            LEFT JOIN p.interviewRequest req
            LEFT JOIN req.candidate c
            LEFT JOIN p.interviewSchedule sch
            LEFT JOIN sch.request sreq
            LEFT JOIN sreq.candidate rc
            WHERE p.createdAt >= :from
            GROUP BY COALESCE(c.id, rc.id), COALESCE(c.name, rc.name)
            """)
    List<Object[]> countByCandidateSince(@Param("from") LocalDateTime from);
}
