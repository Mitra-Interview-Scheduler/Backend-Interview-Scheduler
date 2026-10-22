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

    boolean existsByCandidateIdAndTechnologyId(Long candidateId, Long technologyId);

    @Query("""
            SELECT ct FROM CandidateTechnology ct
            JOIN FETCH ct.technology t
            JOIN FETCH ct.candidate c
            WHERE ct.isActive = true AND t.isActive = false
            """)
    List<CandidateTechnology> findOrphanActiveCandidateTechnologies();
}
