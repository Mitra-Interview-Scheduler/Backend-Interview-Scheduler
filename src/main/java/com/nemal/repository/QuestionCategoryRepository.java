package com.nemal.repository;

import com.nemal.entity.QuestionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, Long> {
    List<QuestionCategory> findByIsActiveTrueOrderByDisplayOrderAscLabelAsc();

    List<QuestionCategory> findByIsActiveTrueAndIsSystemFalseOrderByDisplayOrderAscLabelAsc();

    Optional<QuestionCategory> findByCodeIgnoreCase(String code);

    Optional<QuestionCategory> findByLabelIgnoreCase(String label);
}
