package com.nemal.repository;

import com.nemal.entity.CandidatePipelineStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidatePipelineStatusEventRepository extends JpaRepository<CandidatePipelineStatusEvent, Long> {

    List<CandidatePipelineStatusEvent> findByCandidate_IdOrderByCreatedAtDesc(Long candidateId);
}
