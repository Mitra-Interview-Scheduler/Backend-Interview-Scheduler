package com.nemal.repository;

import com.nemal.entity.InterviewPanel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewPanelRepository extends JpaRepository<InterviewPanel, Long> {

    @Query("SELECT DISTINCT p FROM InterviewPanel p " +
            "LEFT JOIN FETCH p.panelRequests r " +
            "LEFT JOIN FETCH r.assignedInterviewer " +
            "LEFT JOIN FETCH r.interviewSchedule " +
            "LEFT JOIN FETCH r.requiredTechnologies " +
            "LEFT JOIN FETCH p.candidate " +
            "WHERE p.candidate.id = :candidateId " +
            "OR (:candidateName IS NOT NULL AND LOWER(TRIM(p.candidateName)) = LOWER(TRIM(:candidateName))) " +
            "ORDER BY p.startDateTime DESC")
    List<InterviewPanel> findByCandidateIdOrName(
            @Param("candidateId") Long candidateId,
            @Param("candidateName") String candidateName);

    @Query("SELECT DISTINCT p FROM InterviewPanel p " +
            "LEFT JOIN FETCH p.panelRequests r " +
            "LEFT JOIN FETCH r.assignedInterviewer " +
            "LEFT JOIN FETCH r.interviewSchedule " +
            "LEFT JOIN FETCH r.requiredTechnologies " +
            "WHERE p.candidate.id = :candidateId " +
            "ORDER BY p.startDateTime DESC")
    List<InterviewPanel> findByCandidateId(@Param("candidateId") Long candidateId);

    @Query("SELECT DISTINCT p FROM InterviewPanel p " +
            "LEFT JOIN FETCH p.panelRequests r " +
            "LEFT JOIN FETCH r.assignedInterviewer " +
            "LEFT JOIN FETCH r.requiredTechnologies " +
            "WHERE p.requestedBy.id = :userId " +
            "ORDER BY p.startDateTime DESC")
    List<InterviewPanel> findByRequestedById(@Param("userId") Long userId);

    @Query("SELECT DISTINCT p FROM InterviewPanel p " +
            "LEFT JOIN FETCH p.panelRequests r " +
            "LEFT JOIN FETCH r.assignedInterviewer " +
            "LEFT JOIN FETCH r.requiredTechnologies " +
            "LEFT JOIN p.candidate c " +
            "LEFT JOIN c.targetDesignation cd " +
            "LEFT JOIN cd.tier ct " +
            "WHERE p.requestedBy.id = :userId " +
            "AND (:departmentId IS NULL OR c.department.id = :departmentId) " +
            "AND (:minTierOrder IS NULL OR (ct.tierOrder IS NOT NULL AND ct.tierOrder >= :minTierOrder)) " +
            "AND (:exactTierOrder IS NULL OR (ct.tierOrder IS NOT NULL AND ct.tierOrder = :exactTierOrder)) " +
            "ORDER BY p.startDateTime DESC")
    List<InterviewPanel> findByRequestedByIdWithFilters(
            @Param("userId") Long userId,
            @Param("departmentId") Long departmentId,
            @Param("minTierOrder") Integer minTierOrder,
            @Param("exactTierOrder") Integer exactTierOrder
    );

    @Query("SELECT DISTINCT p FROM InterviewPanel p " +
            "LEFT JOIN FETCH p.panelRequests r " +
            "LEFT JOIN FETCH r.assignedInterviewer " +
            "LEFT JOIN FETCH r.requiredTechnologies " +
            "WHERE p.id = :id")
    Optional<InterviewPanel> findByIdWithDetails(@Param("id") Long id);
}