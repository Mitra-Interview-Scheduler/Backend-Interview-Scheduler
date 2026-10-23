package com.nemal.repository;

import com.nemal.entity.CandidateDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CandidateDomainRepository extends JpaRepository<CandidateDomain, Long> {
    List<CandidateDomain> findByCandidateId(Long candidateId);

    List<CandidateDomain> findByCandidateIdIn(Collection<Long> candidateIds);

    /** Domain names for several candidates: (candidateId, domainName). */
    @Query("""
            SELECT cd.candidate.id, d.name FROM CandidateDomain cd
            JOIN cd.domain d
            WHERE cd.candidate.id IN :candidateIds
            """)
    List<Object[]> findDomainNamesByCandidateIds(@Param("candidateIds") Collection<Long> candidateIds);

    boolean existsByCandidateIdAndDomainId(Long candidateId, Long domainId);
}
