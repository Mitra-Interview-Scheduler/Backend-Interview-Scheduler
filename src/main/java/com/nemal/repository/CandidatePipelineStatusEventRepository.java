package com.nemal.repository;

import com.nemal.entity.CandidatePipelineStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
