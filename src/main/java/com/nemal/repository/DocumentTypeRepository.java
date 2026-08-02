package com.nemal.repository;

import com.nemal.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    List<DocumentType> findByActiveTrueOrderByDisplayOrderAscLabelAsc();

    List<DocumentType> findAllByOrderByDisplayOrderAscLabelAsc();

    Optional<DocumentType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
