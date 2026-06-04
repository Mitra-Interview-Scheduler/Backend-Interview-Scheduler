package com.nemal.repository;

import com.nemal.entity.CandidateStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateStepRepository extends JpaRepository<CandidateStep, Long> {
    List<CandidateStep> findByIsActiveTrueOrderByStepOrderAscDisplayOrderAsc();
}
