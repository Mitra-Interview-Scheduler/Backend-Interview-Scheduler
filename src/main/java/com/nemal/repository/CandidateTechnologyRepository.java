package com.nemal.repository;

import com.nemal.entity.CandidateTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CandidateTechnologyRepository extends JpaRepository<CandidateTechnology, Long> {

    List<CandidateTechnology> findByCandidateId(Long candidateId);

    List<CandidateTechnology> findByCandidateIdIn(Collection<Long> candidateIds);

    List<CandidateTechnology> findByCandidateIdAndIsActiveTrue(Long candidateId);

    boolean existsByCandidateIdAndTechnologyId(Long candidateId, Long technologyId);
}
