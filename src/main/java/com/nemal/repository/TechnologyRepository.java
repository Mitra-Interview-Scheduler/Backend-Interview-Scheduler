package com.nemal.repository;

import com.nemal.entity.Technology;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TechnologyRepository extends JpaRepository<Technology, Long> {
    Technology findByNameIgnoreCase(String name);

    Optional<Technology> findByCodeIgnoreCase(String code);

    List<Technology> findByIsActiveTrueAndCategory_CodeIgnoreCaseOrderByNameAsc(String categoryCode);
}