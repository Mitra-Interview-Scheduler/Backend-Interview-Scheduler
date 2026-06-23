package com.nemal.service;

import com.nemal.dto.CreateQuestionCategoryDto;
import com.nemal.dto.QuestionCategoryDto;
import com.nemal.dto.UpdateQuestionCategoryDto;
import com.nemal.entity.QuestionCategory;
import com.nemal.repository.QuestionCategoryRepository;
import com.nemal.util.LookupCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionCategoryService {
    private final QuestionCategoryRepository categoryRepository;

    public QuestionCategoryService(QuestionCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionCategoryDto> getActiveCategories(boolean forFormsOnly) {
        var categories = forFormsOnly
                ? categoryRepository.findByIsActiveTrueAndIsSystemFalseOrderByDisplayOrderAscLabelAsc()
                : categoryRepository.findByIsActiveTrueOrderByDisplayOrderAscLabelAsc();
        return categories.stream().map(QuestionCategoryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public QuestionCategoryDto getCategoryById(Long id) {
        QuestionCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question category not found"));
        return QuestionCategoryDto.from(category);
    }

    @Transactional
    public QuestionCategoryDto createCategory(CreateQuestionCategoryDto dto) {
        String code = resolveCode(dto.code(), dto.label());
        if (categoryRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new RuntimeException("Question category with this code already exists");
        }
        if (categoryRepository.findByLabelIgnoreCase(dto.label().trim()).isPresent()) {
            throw new RuntimeException("Question category with this label already exists");
        }

        QuestionCategory category = QuestionCategory.builder()
                .code(code)
                .label(dto.label().trim())
                .displayOrder(dto.displayOrder() != null ? dto.displayOrder() : 50)
                .isActive(true)
                .isSystem(false)
                .build();

        return QuestionCategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public QuestionCategoryDto updateCategory(Long id, UpdateQuestionCategoryDto dto) {
        QuestionCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question category not found"));

        if (category.isSystem()) {
            throw new RuntimeException("System question categories cannot be modified");
        }

        if (dto.code() != null) {
            String code = resolveCode(dto.code(), category.getLabel());
            categoryRepository.findByCodeIgnoreCase(code)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Question category with this code already exists");
                    });
            category.setCode(code);
        }
        if (dto.label() != null) {
            String label = dto.label().trim();
            categoryRepository.findByLabelIgnoreCase(label)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Question category with this label already exists");
                    });
            category.setLabel(label);
        }
        if (dto.displayOrder() != null) {
            category.setDisplayOrder(dto.displayOrder());
        }
        if (dto.isActive() != null) {
            category.setActive(dto.isActive());
        }

        return QuestionCategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        QuestionCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question category not found"));
        if (category.isSystem()) {
            throw new RuntimeException("System question categories cannot be deleted");
        }
        category.setActive(false);
        categoryRepository.save(category);
    }

    QuestionCategory requireActiveCategory(Long categoryId) {
        QuestionCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Question category not found"));
        if (!category.isActive()) {
            throw new RuntimeException("Question category is inactive");
        }
        if (category.isSystem()) {
            throw new RuntimeException("System question categories cannot be assigned to form questions");
        }
        return category;
    }

    QuestionCategory resolveCategory(Long categoryId, String categoryLabel) {
        if (categoryId != null) {
            return requireActiveCategory(categoryId);
        }
        if (categoryLabel != null && !categoryLabel.isBlank()) {
            return categoryRepository.findByLabelIgnoreCase(categoryLabel.trim())
                    .filter(QuestionCategory::isActive)
                    .filter(category -> !category.isSystem())
                    .orElseThrow(() -> new RuntimeException("Question category not found: " + categoryLabel));
        }
        throw new RuntimeException("Question category is required");
    }

    QuestionCategory requireObligatoryCategory() {
        return categoryRepository.findByCodeIgnoreCase("OBLIGATORY")
                .filter(QuestionCategory::isActive)
                .orElseThrow(() -> new RuntimeException("Obligatory question category not found"));
    }

    private String resolveCode(String code, String label) {
        String resolved = (code != null && !code.isBlank()) ? LookupCodeUtils.toCode(code) : LookupCodeUtils.toCode(label);
        if (resolved.isBlank()) {
            throw new RuntimeException("Category code or label is required");
        }
        return resolved;
    }
}
