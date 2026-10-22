package com.nemal.repository;

import com.nemal.entity.Domain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomainRepository extends JpaRepository<Domain, Long> {
    List<Domain> findByIsActiveTrueOrderByNameAsc();

    Optional<Domain> findByNameIgnoreCase(String name);

    Optional<Domain> findByCodeIgnoreCase(String code);

    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM Domain d
            WHERE d.isActive = true
              AND NOT EXISTS (SELECT 1 FROM CandidateDomain cd WHERE cd.domain.id = d.id)
              AND NOT EXISTS (SELECT 1 FROM UserDomain ud WHERE ud.domain.id = d.id)
            ORDER BY d.name
            """)
    List<Domain> findUnusedActiveDomains();
}
