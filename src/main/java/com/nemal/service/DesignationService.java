package com.nemal.service;

import com.nemal.dto.CreateDesignationDto;
import com.nemal.dto.DesignationDto;
import com.nemal.dto.UpdateDesignationDto;
import com.nemal.entity.Department;
import com.nemal.entity.Designation;
import com.nemal.entity.Tier;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.DesignationRepository;
import com.nemal.repository.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DesignationService {

    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final TierRepository tierRepository;

    public DesignationService(DesignationRepository designationRepository,
                              DepartmentRepository departmentRepository,
                              TierRepository tierRepository) {
        this.designationRepository = designationRepository;
        this.departmentRepository = departmentRepository;
        this.tierRepository = tierRepository;
    }

    public List<DesignationDto> getAllDesignations() {
        return designationRepository.findByIsActiveTrue().stream()
                .map(DesignationDto::from)
                .collect(Collectors.toList());
    }

    public List<DesignationDto> getAllDesignationsIncludingInactive() {
        return designationRepository.findAll().stream()
                .map(DesignationDto::from)
                .collect(Collectors.toList());
    }

    public List<DesignationDto> getDesignationsByDepartment(Long departmentId) {
        return designationRepository.findByDepartmentIdAndIsActiveTrue(departmentId).stream()
                .map(DesignationDto::from)
                .collect(Collectors.toList());
    }

    public List<DesignationDto> getDesignationsByTier(Long tierId) {
        return designationRepository.findByTierIdAndIsActiveTrueOrderByLevelOrderAsc(tierId).stream()
                .map(DesignationDto::from)
                .collect(Collectors.toList());
    }

    public DesignationDto getDesignationById(Long id) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Designation not found"));
        return DesignationDto.from(designation);
    }

    @Transactional
    public DesignationDto createDesignation(CreateDesignationDto dto) {
        Department department = departmentRepository.findById(dto.departmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        Tier tier = tierRepository.findById(dto.tierId())
                .orElseThrow(() -> new RuntimeException("Tier not found"));

        // Verify tier belongs to the department
        if (!tier.getDepartment().getId().equals(dto.departmentId())) {
            throw new IllegalArgumentException(
                    "Tier '" + tier.getName() + "' does not belong to department '" + department.getName() + "'"
            );
        }

        // Verify tier is active
        if (!tier.isActive()) {
            throw new IllegalArgumentException("Cannot create designation in inactive tier");
        }

        // Check if designation name already exists in this department
        boolean nameExists = designationRepository.existsByDepartmentIdAndNameAndIsActiveTrue(
                dto.departmentId(),
                dto.name()
        );

        if (nameExists) {
            throw new IllegalArgumentException(
                    "Designation name '" + dto.name() + "' already exists in department: " + department.getName() +
                            ". Please use a different name or deactivate the existing one."
            );
        }

        // Check if level order already exists in active designations for this tier
        boolean levelExists = designationRepository.existsByTierIdAndLevelOrderAndIsActiveTrue(
                dto.tierId(),
                dto.levelOrder()
        );

        if (levelExists) {
            throw new IllegalArgumentException(
                    "Level order " + dto.levelOrder() + " already exists in tier: " + tier.getName()
            );
        }

        Designation designation = Designation.builder()
                .name(dto.name())
                .levelOrder(dto.levelOrder())
                .department(department)
                .tier(tier)
                .description(dto.description())
                .isActive(true)
                .build();

        designation = designationRepository.save(designation);
        return DesignationDto.from(designation);
    }

    @Transactional
    public DesignationDto updateDesignation(Long id, UpdateDesignationDto dto) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Designation not found"));

        if (dto.name() != null) {
            designation.setName(dto.name());
        }

        if (dto.description() != null) {
            designation.setDescription(dto.description());
        }

        if (dto.isActive() != null) {
            designation.setActive(dto.isActive());
        }

        // Handle tier change
        if (dto.tierId() != null && !dto.tierId().equals(designation.getTier().getId())) {
            Tier newTier = tierRepository.findById(dto.tierId())
                    .orElseThrow(() -> new RuntimeException("Tier not found"));

            // Verify new tier belongs to same department
            if (!newTier.getDepartment().getId().equals(designation.getDepartment().getId())) {
                throw new IllegalArgumentException(
                        "New tier must belong to the same department"
                );
            }

            // Verify new tier is active
            if (!newTier.isActive()) {
                throw new IllegalArgumentException("Cannot move designation to inactive tier");
            }

            designation.setTier(newTier);
        }

        // Handle level order change - only validate if level or tier is changing
        boolean levelChanged = dto.levelOrder() != null && !dto.levelOrder().equals(designation.getLevelOrder());
        boolean tierChanged = dto.tierId() != null && !dto.tierId().equals(designation.getTier().getId());

        if (levelChanged || tierChanged) {
            Long targetTierId = tierChanged ? dto.tierId() : designation.getTier().getId();
            Integer targetLevel = levelChanged ? dto.levelOrder() : designation.getLevelOrder();

            // Check if new level conflicts with another active designation
            boolean levelExists = designationRepository.existsByTierIdAndLevelOrderAndIsActiveTrueAndIdNot(
                    targetTierId,
                    targetLevel,
                    designation.getId()
            );

            if (levelExists) {
                throw new IllegalArgumentException(
                        "Level order " + targetLevel + " is already used in the target tier"
                );
            }

            if (levelChanged) {
                designation.setLevelOrder(dto.levelOrder());
            }
        }

        designation = designationRepository.save(designation);
        return DesignationDto.from(designation);
    }

    @Transactional
    public void deleteDesignation(Long id) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Designation not found"));

        // Soft delete - set inactive
        designation.setActive(false);
        designationRepository.save(designation);
    }
}