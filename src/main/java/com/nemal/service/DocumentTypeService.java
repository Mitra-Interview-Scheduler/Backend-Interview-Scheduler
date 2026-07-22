package com.nemal.service;

import com.nemal.dto.CatalogTypeDto;
import com.nemal.dto.CreateCatalogTypeDto;
import com.nemal.dto.UpdateCatalogTypeDto;
import com.nemal.entity.DocumentType;
import com.nemal.repository.DocumentTypeRepository;
import com.nemal.util.LookupCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;

    public DocumentTypeService(DocumentTypeRepository documentTypeRepository) {
        this.documentTypeRepository = documentTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogTypeDto> listActive() {
        return documentTypeRepository.findByActiveTrueOrderByDisplayOrderAscLabelAsc().stream()
                .map(CatalogTypeDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogTypeDto> listAll() {
        return documentTypeRepository.findAllByOrderByDisplayOrderAscLabelAsc().stream()
                .map(CatalogTypeDto::from)
                .toList();
    }

    @Transactional
    public CatalogTypeDto create(CreateCatalogTypeDto dto) {
        String label = requireLabel(dto.label());
        String code = resolveCode(dto.code(), label, null);
        if (documentTypeRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new IllegalArgumentException("Document type code already exists");
        }

        DocumentType type = DocumentType.builder()
                .code(code)
                .label(label)
                .displayOrder(dto.displayOrder() != null ? dto.displayOrder() : nextOrder())
                .active(true)
                .build();
        return CatalogTypeDto.from(documentTypeRepository.save(type));
    }

    @Transactional
    public CatalogTypeDto update(Long id, UpdateCatalogTypeDto dto) {
        DocumentType type = documentTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document type not found"));

        if (dto.label() != null && !dto.label().isBlank()) {
            type.setLabel(dto.label().trim());
        }
        if (dto.code() != null && !dto.code().isBlank()) {
            String code = resolveCode(dto.code(), type.getLabel(), id);
            if (documentTypeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
                throw new IllegalArgumentException("Document type code already exists");
            }
            type.setCode(code);
        }
        if (dto.displayOrder() != null) {
            type.setDisplayOrder(dto.displayOrder());
        }
        if (dto.active() != null) {
            type.setActive(dto.active());
        }
        return CatalogTypeDto.from(documentTypeRepository.save(type));
    }

    @Transactional
    public void delete(Long id) {
        DocumentType type = documentTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document type not found"));
        type.setActive(false);
        documentTypeRepository.save(type);
    }

    private int nextOrder() {
        return documentTypeRepository.findAll().stream()
                .mapToInt(DocumentType::getDisplayOrder)
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
                    ? documentTypeRepository.findByCodeIgnoreCase(candidate).isPresent()
                    : documentTypeRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
            if (!taken) {
                return candidate;
            }
            candidate = base + "_" + suffix;
            suffix += 1;
        }
    }
}
