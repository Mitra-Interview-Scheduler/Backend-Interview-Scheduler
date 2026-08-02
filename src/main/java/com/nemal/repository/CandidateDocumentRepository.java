package com.nemal.repository;

import com.nemal.entity.CandidateDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, Long> {
    List<CandidateDocument> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    Optional<CandidateDocument> findByIdAndCandidateId(Long id, Long candidateId);

    @Query("""
            SELECT d FROM CandidateDocument d
            WHERE d.candidate.id IN :candidateIds
            AND UPPER(d.documentType) IN ('PROFILE', 'PROFILE_PICTURE')
            ORDER BY d.updatedAt DESC
            """)
    List<CandidateDocument> findProfilePictureCandidatesByCandidateIds(
            @Param("candidateIds") Collection<Long> candidateIds);
}
