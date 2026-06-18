package com.nemal.repository;

import com.nemal.entity.TechnologyCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TechnologyCategoryRepository extends JpaRepository<TechnologyCategory, Long> {
    List<TechnologyCategory> findByIsActiveTrueOrderByDisplayOrderAscLabelAsc();

    Optional<TechnologyCategory> findByCodeIgnoreCase(String code);

    Optional<TechnologyCategory> findByLabelIgnoreCase(String label);
}
