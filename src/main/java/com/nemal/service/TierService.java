package com.nemal.service;

import com.nemal.dto.CreateTierDto;
import com.nemal.dto.TierDto;
import com.nemal.dto.UpdateTierDto;
import com.nemal.entity.Department;
import com.nemal.entity.Tier;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TierService {

    private final TierRepository tierRepository;
    private final DepartmentRepository departmentRepository;

    public TierService(TierRepository tierRepository, DepartmentRepository departmentRepository) {
        this.tierRepository = tierRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<TierDto> getAllTiers() {
        return tierRepository.findByIsActiveTrueOrderByTierOrderAsc().stream()
                .map(TierDto::from)
                .collect(Collectors.toList());
    }

    public List<TierDto> getAllTiersIncludingInactive() {
        return tierRepository.findAll().stream()
                .map(TierDto::from)
                .collect(Collectors.toList());
    }

    public List<TierDto> getTiersByDepartment(Long departmentId) {
        return tierRepository.findByDepartmentIdAndIsActiveTrueOrderByTierOrderAsc(departmentId).stream()
                .map(TierDto::from)
                .collect(Collectors.toList());
    }

    public TierDto getTierById(Long id) {
        Tier tier = tierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tier not found"));
        return TierDto.from(tier);
    }

    @Transactional
    public TierDto createTier(CreateTierDto dto) {
        Department department = departmentRepository.findById(dto.departmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        // Check if tier order already exists for active tiers in this department
        boolean orderExists = tierRepository.existsByDepartmentIdAndTierOrderAndIsActiveTrue(
                dto.departmentId(),
                dto.tierOrder()
        );

        if (orderExists) {
            throw new IllegalArgumentException(
                    "Tier order " + dto.tierOrder() + " already exists in department: " + department.getName()
            );
        }

        Tier tier = Tier.builder()
                .name(dto.name())
                .department(department)
                .tierOrder(dto.tierOrder())
                .description(dto.description())
                .isActive(true)
                .build();

        tier = tierRepository.save(tier);
        return TierDto.from(tier);
    }

    @Transactional
    public TierDto updateTier(Long id, UpdateTierDto dto) {
        Tier tier = tierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tier not found"));

        if (dto.name() != null) {
            tier.setName(dto.name());
        }

        if (dto.description() != null) {
            tier.setDescription(dto.description());
        }

        if (dto.isActive() != null) {
            tier.setActive(dto.isActive());
        }

        // Only validate tier order if it's changing
        if (dto.tierOrder() != null && !dto.tierOrder().equals(tier.getTierOrder())) {
            // Check if new order conflicts with another active tier
            boolean orderExists = tierRepository.existsByDepartmentIdAndTierOrderAndIsActiveTrueAndIdNot(
                    tier.getDepartment().getId(),
                    dto.tierOrder(),
                    tier.getId()
            );

            if (orderExists) {
                throw new IllegalArgumentException(
                        "Tier order " + dto.tierOrder() + " is already used by another tier in this department"
                );
            }

            tier.setTierOrder(dto.tierOrder());
        }

        tier = tierRepository.save(tier);
        return TierDto.from(tier);
    }

    @Transactional
    public void deleteTier(Long id) {
        Tier tier = tierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tier not found"));

        // Soft delete - set inactive
        tier.setActive(false);
        tierRepository.save(tier);
    }
}