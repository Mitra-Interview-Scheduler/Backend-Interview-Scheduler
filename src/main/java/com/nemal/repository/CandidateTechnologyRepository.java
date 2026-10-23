package com.nemal.repository;

import com.nemal.entity.CandidateTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CandidateTechnologyRepository extends JpaRepository<CandidateTechnology, Long> {

    List<CandidateTechnology> findByCandidateId(Long candidateId);

    List<CandidateTechnology> findByCandidateIdIn(Collection<Long> candidateIds);

    List<CandidateTechnology> findByCandidateIdAndIsActiveTrue(Long candidateId);

    /** Active technology names for several candidates: (candidateId, technologyName). */
    @Query("""
            SELECT ct.candidate.id, t.name FROM CandidateTechnology ct
            JOIN ct.technology t
            WHERE ct.candidate.id IN :candidateIds
              AND ct.isActive = true
            """)
    List<Object[]> findActiveTechnologyNamesByCandidateIds(
            @org.springframework.data.repository.query.Param("candidateIds") Collection<Long> candidateIds);

    boolean existsByCandidateIdAndTechnologyId(Long candidateId, Long technologyId);

    @Query("""
            SELECT ct FROM CandidateTechnology ct
            JOIN FETCH ct.technology t
            JOIN FETCH ct.candidate c
            WHERE ct.isActive = true AND t.isActive = false
            """)
    List<CandidateTechnology> findOrphanActiveCandidateTechnologies();
}
