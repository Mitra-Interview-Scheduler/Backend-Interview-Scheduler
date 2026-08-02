package com.nemal.repository;

import com.nemal.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceTypeRepository extends JpaRepository<ResourceType, Long> {
    List<ResourceType> findByActiveTrueOrderByDisplayOrderAscLabelAsc();

    List<ResourceType> findAllByOrderByDisplayOrderAscLabelAsc();

    Optional<ResourceType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
