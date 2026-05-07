package com.nemal.repository;

import com.nemal.entity.CandidateDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, Long> {
    List<CandidateDocument> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    Optional<CandidateDocument> findByIdAndCandidateId(Long id, Long candidateId);
}
