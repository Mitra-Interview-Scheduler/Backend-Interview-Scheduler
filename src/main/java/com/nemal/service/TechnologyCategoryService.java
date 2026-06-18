package com.nemal.service;

import com.nemal.dto.CreateTechnologyCategoryDto;
import com.nemal.dto.TechnologyCategoryDto;
import com.nemal.dto.UpdateTechnologyCategoryDto;
import com.nemal.entity.TechnologyCategory;
import com.nemal.repository.TechnologyCategoryRepository;
import com.nemal.util.LookupCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TechnologyCategoryService {
    private final TechnologyCategoryRepository categoryRepository;

    public TechnologyCategoryService(TechnologyCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<TechnologyCategoryDto> getActiveCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAscLabelAsc()
                .stream()
                .map(TechnologyCategoryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TechnologyCategoryDto getCategoryById(Long id) {
        TechnologyCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Technology category not found"));
        return TechnologyCategoryDto.from(category);
    }

    @Transactional
    public TechnologyCategoryDto createCategory(CreateTechnologyCategoryDto dto) {
        String code = resolveCode(dto.code(), dto.label());
        if (categoryRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new RuntimeException("Technology category with this code already exists");
        }
        if (categoryRepository.findByLabelIgnoreCase(dto.label().trim()).isPresent()) {
            throw new RuntimeException("Technology category with this label already exists");
        }

        TechnologyCategory category = TechnologyCategory.builder()
                .code(code)
                .label(dto.label().trim())
                .displayOrder(dto.displayOrder() != null ? dto.displayOrder() : 50)
                .isActive(true)
                .build();

        return TechnologyCategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public TechnologyCategoryDto updateCategory(Long id, UpdateTechnologyCategoryDto dto) {
        TechnologyCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Technology category not found"));

        if (dto.code() != null) {
            String code = resolveCode(dto.code(), category.getLabel());
            categoryRepository.findByCodeIgnoreCase(code)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Technology category with this code already exists");
                    });
            category.setCode(code);
        }
        if (dto.label() != null) {
            String label = dto.label().trim();
            categoryRepository.findByLabelIgnoreCase(label)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Technology category with this label already exists");
                    });
            category.setLabel(label);
        }
        if (dto.displayOrder() != null) {
            category.setDisplayOrder(dto.displayOrder());
        }
        if (dto.isActive() != null) {
            category.setActive(dto.isActive());
        }

        return TechnologyCategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        TechnologyCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Technology category not found"));
        category.setActive(false);
        categoryRepository.save(category);
    }

    TechnologyCategory requireActiveCategory(Long categoryId) {
        TechnologyCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Technology category not found"));
        if (!category.isActive()) {
            throw new RuntimeException("Technology category is inactive");
        }
        return category;
    }

    private String resolveCode(String code, String label) {
        String resolved = (code != null && !code.isBlank()) ? LookupCodeUtils.toCode(code) : LookupCodeUtils.toCode(label);
        if (resolved.isBlank()) {
            throw new RuntimeException("Category code or label is required");
        }
        return resolved;
    }
}
