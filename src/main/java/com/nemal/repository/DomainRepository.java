package com.nemal.repository;

import com.nemal.entity.Domain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomainRepository extends JpaRepository<Domain, Long> {
    List<Domain> findByIsActiveTrueOrderByNameAsc();

    Optional<Domain> findByNameIgnoreCase(String name);

    Optional<Domain> findByCodeIgnoreCase(String code);
}
