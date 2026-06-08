package com.nemal.repository;

import com.nemal.entity.CandidateScreening;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CandidateScreeningRepository extends JpaRepository<CandidateScreening, Long> {
    Optional<CandidateScreening> findByCandidateId(Long candidateId);
}