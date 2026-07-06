package com.nemal.service;

import com.nemal.dto.CreateDomainDto;
import com.nemal.dto.DomainDto;
import com.nemal.dto.UpdateDomainDto;
import com.nemal.entity.Domain;
import com.nemal.repository.DomainRepository;
import com.nemal.util.LookupCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DomainService {

    private final DomainRepository domainRepository;

    public DomainService(DomainRepository domainRepository) {
        this.domainRepository = domainRepository;
    }

    @Transactional(readOnly = true)
    public List<DomainDto> getAllDomains() {
        return domainRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(DomainDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DomainDto> getAllDomainsIncludingInactive() {
        return domainRepository.findAll().stream()
                .map(DomainDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DomainDto getDomainById(Long id) {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Domain not found"));
        return DomainDto.from(domain);
    }

    @Transactional(readOnly = true)
    public Domain requireActiveDomain(Long id) {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Domain not found"));
        if (!domain.isActive()) {
            throw new RuntimeException("Domain is inactive");
        }
        return domain;
    }

    @Transactional
    public DomainDto createDomain(CreateDomainDto dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Domain name is required");
        }

        domainRepository.findByNameIgnoreCase(dto.name().trim()).ifPresent(existing -> {
            throw new IllegalArgumentException("Domain with this name already exists");
        });

        String code = resolveUniqueCode(dto.code(), dto.name(), null);

        Domain domain = Domain.builder()
                .name(dto.name().trim())
                .code(code)
                .isActive(true)
                .build();

        domain = domainRepository.save(domain);
        return DomainDto.from(domain);
    }

    @Transactional
    public DomainDto updateDomain(Long id, UpdateDomainDto dto) {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Domain not found"));

        if (dto.name() != null) {
            String trimmed = dto.name().trim();
            domainRepository.findByNameIgnoreCase(trimmed).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Domain with this name already exists");
                }
            });
            domain.setName(trimmed);
        }

        if (dto.code() != null) {
            domain.setCode(resolveUniqueCode(dto.code(), domain.getName(), id));
        }

        if (dto.isActive() != null) {
            domain.setActive(dto.isActive());
        }

        domain = domainRepository.save(domain);
        return DomainDto.from(domain);
    }

    @Transactional
    public void deleteDomain(Long id) {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Domain not found"));
        domain.setActive(false);
        domainRepository.save(domain);
    }

    private String resolveUniqueCode(String requestedCode, String name, Long excludeId) {
        String baseCode = (requestedCode != null && !requestedCode.isBlank())
                ? LookupCodeUtils.toCode(requestedCode)
                : LookupCodeUtils.toCode(name);
        if (baseCode.isBlank()) {
            throw new IllegalArgumentException("Domain code or name is required");
        }

        String candidate = baseCode;
        int suffix = 1;
        while (true) {
            var existing = domainRepository.findByCodeIgnoreCase(candidate);
            if (existing.isEmpty() || (excludeId != null && existing.get().getId().equals(excludeId))) {
                return candidate;
            }
            candidate = baseCode + "_" + suffix++;
        }
    }
}
