package com.nemal.repository;

import com.nemal.entity.CandidateClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CandidateClosureRepository extends JpaRepository<CandidateClosure, Long> {
    Optional<CandidateClosure> findTopByCandidateIdOrderByClosedAtDesc(Long candidateId);
}
