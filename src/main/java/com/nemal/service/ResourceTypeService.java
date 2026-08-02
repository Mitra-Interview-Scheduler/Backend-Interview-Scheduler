package com.nemal.service;

import com.nemal.dto.CatalogTypeDto;
import com.nemal.dto.CreateCatalogTypeDto;
import com.nemal.dto.UpdateCatalogTypeDto;
import com.nemal.entity.ResourceType;
import com.nemal.repository.ResourceTypeRepository;
import com.nemal.util.LookupCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResourceTypeService {

    private final ResourceTypeRepository resourceTypeRepository;

    public ResourceTypeService(ResourceTypeRepository resourceTypeRepository) {
        this.resourceTypeRepository = resourceTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogTypeDto> listActive() {
        return resourceTypeRepository.findByActiveTrueOrderByDisplayOrderAscLabelAsc().stream()
                .map(CatalogTypeDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogTypeDto> listAll() {
        return resourceTypeRepository.findAllByOrderByDisplayOrderAscLabelAsc().stream()
                .map(CatalogTypeDto::from)
                .toList();
    }

    @Transactional
    public CatalogTypeDto create(CreateCatalogTypeDto dto) {
        String label = requireLabel(dto.label());
        String code = resolveCode(dto.code(), label, null);

        ResourceType type = ResourceType.builder()
                .code(code)
                .label(label)
                .displayOrder(dto.displayOrder() != null ? dto.displayOrder() : nextOrder())
                .active(true)
                .build();
        return CatalogTypeDto.from(resourceTypeRepository.save(type));
    }

    @Transactional
    public CatalogTypeDto update(Long id, UpdateCatalogTypeDto dto) {
        ResourceType type = resourceTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resource type not found"));

        if (dto.label() != null && !dto.label().isBlank()) {
            type.setLabel(dto.label().trim());
        }
        if (dto.code() != null && !dto.code().isBlank()) {
            String code = resolveCode(dto.code(), type.getLabel(), id);
            type.setCode(code);
        }
        if (dto.displayOrder() != null) {
            type.setDisplayOrder(dto.displayOrder());
        }
        if (dto.active() != null) {
            type.setActive(dto.active());
        }
        return CatalogTypeDto.from(resourceTypeRepository.save(type));
    }

    @Transactional
    public void delete(Long id) {
        ResourceType type = resourceTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resource type not found"));
        type.setActive(false);
        resourceTypeRepository.save(type);
    }

    private int nextOrder() {
        return resourceTypeRepository.findAll().stream()
                .mapToInt(ResourceType::getDisplayOrder)
                .max()
                .orElse(0) + 1;
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label is required");
        }
        return label.trim();
    }

    private String resolveCode(String rawCode, String label, Long excludeId) {
        String base = (rawCode != null && !rawCode.isBlank())
                ? LookupCodeUtils.toCode(rawCode)
                : LookupCodeUtils.toCode(label);
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("Code is required");
        }
        String candidate = base;
        int suffix = 2;
        while (true) {
            boolean taken = excludeId == null
                    ? resourceTypeRepository.findByCodeIgnoreCase(candidate).isPresent()
                    : resourceTypeRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
            if (!taken) {
                return candidate;
            }
            candidate = base + "_" + suffix;
            suffix += 1;
        }
    }
}
