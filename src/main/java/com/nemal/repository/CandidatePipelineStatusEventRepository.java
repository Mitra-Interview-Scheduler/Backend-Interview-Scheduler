package com.nemal.repository;

import com.nemal.entity.CandidatePipelineStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface CandidatePipelineStatusEventRepository extends JpaRepository<CandidatePipelineStatusEvent, Long> {

    @Query("""
            SELECT DISTINCT e FROM CandidatePipelineStatusEvent e
            LEFT JOIN FETCH e.masterStep
            LEFT JOIN FETCH e.previousMasterStep
            LEFT JOIN FETCH e.changedBy cb
            LEFT JOIN FETCH cb.currentDesignation
            WHERE e.candidate.id = :candidateId
            ORDER BY e.createdAt DESC
            """)
    List<CandidatePipelineStatusEvent> findByCandidate_IdOrderByCreatedAtDesc(@Param("candidateId") Long candidateId);

    @Query("""
            SELECT e FROM CandidatePipelineStatusEvent e
            LEFT JOIN FETCH e.masterStep
            WHERE e.candidate.id IN :candidateIds
            ORDER BY e.createdAt DESC
            """)
    List<CandidatePipelineStatusEvent> findByCandidateIdIn(@Param("candidateIds") Collection<Long> candidateIds);

    @Query("""
            SELECT e FROM CandidatePipelineStatusEvent e
            LEFT JOIN FETCH e.candidate c
            LEFT JOIN FETCH e.masterStep
            LEFT JOIN FETCH e.previousMasterStep
            WHERE e.createdAt >= :from
            ORDER BY e.createdAt DESC
            """)
    List<CandidatePipelineStatusEvent> findEventsSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT e.candidate.id, MAX(e.createdAt)
            FROM CandidatePipelineStatusEvent e
            WHERE e.candidate.id IN :candidateIds
            GROUP BY e.candidate.id
            """)
    List<Object[]> findLatestEventAtByCandidateIds(@Param("candidateIds") Collection<Long> candidateIds);
}
