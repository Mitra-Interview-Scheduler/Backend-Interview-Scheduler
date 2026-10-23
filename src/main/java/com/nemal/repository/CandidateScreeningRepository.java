package com.nemal.repository;

import com.nemal.entity.Candidate;
import com.nemal.entity.CandidateScreening;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidateScreeningRepository extends JpaRepository<CandidateScreening, Long> {
    Optional<CandidateScreening> findByCandidateId(Long candidateId);

    boolean existsByCandidateId(Long candidateId);

    @Query("""
            SELECT c FROM Candidate c
            LEFT JOIN FETCH c.masterStep ms
            LEFT JOIN FETCH c.coordinatedHr
            LEFT JOIN FETCH c.department
            WHERE c.isActive = true
              AND ms.statusKey IN :statusKeys
              AND NOT EXISTS (SELECT 1 FROM CandidateScreening cs WHERE cs.candidate.id = c.id)
              AND (:coordinatedHrId IS NULL OR c.coordinatedHr.id = :coordinatedHrId)
            ORDER BY c.appliedAt ASC NULLS LAST
            """)
    List<Candidate> findCandidatesNeedingScreening(
            @Param("statusKeys") Collection<String> statusKeys,
            @Param("coordinatedHrId") Long coordinatedHrId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(c) FROM Candidate c
            LEFT JOIN c.masterStep ms
            WHERE c.isActive = true
              AND ms.statusKey IN :statusKeys
              AND NOT EXISTS (SELECT 1 FROM CandidateScreening cs WHERE cs.candidate.id = c.id)
              AND (:coordinatedHrId IS NULL OR c.coordinatedHr.id = :coordinatedHrId)
            """)
    long countCandidatesNeedingScreening(
            @Param("statusKeys") Collection<String> statusKeys,
            @Param("coordinatedHrId") Long coordinatedHrId);
}