package com.nemal.repository;

import com.nemal.entity.CandidateDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CandidateDomainRepository extends JpaRepository<CandidateDomain, Long> {
    List<CandidateDomain> findByCandidateId(Long candidateId);

    List<CandidateDomain> findByCandidateIdIn(Collection<Long> candidateIds);

    boolean existsByCandidateIdAndDomainId(Long candidateId, Long domainId);
}
