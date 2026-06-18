package com.nemal.service;

import com.nemal.dto.CreateTechnologyDto;
import com.nemal.dto.TechnologyCategoryDto;
import com.nemal.dto.TechnologyDto;
import com.nemal.dto.UpdateTechnologyDto;
import com.nemal.entity.Technology;
import com.nemal.entity.TechnologyCategory;
import com.nemal.repository.TechnologyRepository;
import com.nemal.util.LookupCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TechnologyService {

    private final TechnologyRepository technologyRepository;
    private final TechnologyCategoryService categoryService;

    public TechnologyService(
            TechnologyRepository technologyRepository,
            TechnologyCategoryService categoryService
    ) {
        this.technologyRepository = technologyRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public List<TechnologyDto> getAllTechnologies() {
        return technologyRepository.findAll().stream()
                .filter(Technology::isActive)
                .map(TechnologyDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TechnologyDto getTechnologyById(Long id) {
        Technology technology = technologyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Technology not found"));
        return TechnologyDto.from(technology);
    }

    @Transactional(readOnly = true)
    public List<TechnologyDto> getTechnologiesByCategoryCode(String categoryCode) {
        return technologyRepository
                .findByIsActiveTrueAndCategory_CodeIgnoreCaseOrderByNameAsc(categoryCode)
                .stream()
                .map(TechnologyDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TechnologyCategoryDto> getAllCategories() {
        return categoryService.getActiveCategories();
    }

    @Transactional
    public TechnologyDto createTechnology(CreateTechnologyDto dto) {
        Technology existing = technologyRepository.findByNameIgnoreCase(dto.name());
        if (existing != null) {
            throw new RuntimeException("Technology with this name already exists");
        }

        if (dto.categoryId() == null) {
            throw new RuntimeException("Category is required");
        }

        TechnologyCategory category = categoryService.requireActiveCategory(dto.categoryId());
        String code = resolveUniqueCode(dto.code(), dto.name(), null);

        Technology technology = Technology.builder()
                .name(dto.name().trim())
                .code(code)
                .category(category)
                .isActive(true)
                .build();

        technology = technologyRepository.save(technology);
        return TechnologyDto.from(technology);
    }

    @Transactional
    public TechnologyDto updateTechnology(Long id, UpdateTechnologyDto dto) {
        Technology technology = technologyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Technology not found"));

        if (dto.name() != null) {
            Technology existing = technologyRepository.findByNameIgnoreCase(dto.name());
            if (existing != null && !existing.getId().equals(id)) {
                throw new RuntimeException("Technology with this name already exists");
            }
            technology.setName(dto.name().trim());
        }
        if (dto.code() != null) {
            technology.setCode(resolveUniqueCode(dto.code(), technology.getName(), id));
        }
        if (dto.categoryId() != null) {
            technology.setCategory(categoryService.requireActiveCategory(dto.categoryId()));
        }
        if (dto.isActive() != null) {
            technology.setActive(dto.isActive());
        }

        technology = technologyRepository.save(technology);
        return TechnologyDto.from(technology);
    }

    @Transactional
    public void deleteTechnology(Long id) {
        Technology technology = technologyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Technology not found"));
        technology.setActive(false);
        technologyRepository.save(technology);
    }

    private String resolveUniqueCode(String requestedCode, String name, Long excludeId) {
        String baseCode = (requestedCode != null && !requestedCode.isBlank())
                ? LookupCodeUtils.toCode(requestedCode)
                : LookupCodeUtils.toCode(name);
        if (baseCode.isBlank()) {
            throw new RuntimeException("Technology code or name is required");
        }

        String candidate = baseCode;
        int suffix = 1;
        while (true) {
            var existing = technologyRepository.findByCodeIgnoreCase(candidate);
            if (existing.isEmpty() || (excludeId != null && existing.get().getId().equals(excludeId))) {
                return candidate;
            }
            candidate = baseCode + "_" + suffix++;
        }
    }
}
