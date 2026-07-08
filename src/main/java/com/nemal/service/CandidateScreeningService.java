package com.nemal.service;

import com.nemal.dto.ScreeningSaveRequestDTO;
import com.nemal.dto.ScreeningResponseDTO;
import com.nemal.entity.*;
import com.nemal.enums.EngagementType;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateScreeningService {

    private final CandidateScreeningRepository screeningRepository;
    private final CandidateRepository candidateRepository;
    private final DepartmentRepository departmentRepository;
    private final TierRepository tierRepository;
    private final DesignationRepository designationRepository;
    private final CandidatePipelineAuditService candidatePipelineAuditService;

    @Transactional(readOnly = true)
    public ScreeningResponseDTO getScreeningByCandidateId(Long candidateId) {
        return screeningRepository.findByCandidateId(candidateId)
                .map(ScreeningResponseDTO::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException("No active screening profile tracking candidate: " + candidateId));
    }

    @Transactional
    public ScreeningResponseDTO saveOrUpdateScreening(Long candidateId, ScreeningSaveRequestDTO dto, User savedBy) {
        // 1. Verify primary candidate anchor exists
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new EntityNotFoundException("Candidate profile target missing for ID: " + candidateId));

        // 2. Retrieve existing or initialize completely new managed entity
        CandidateScreening screening = screeningRepository.findByCandidateId(candidateId)
                .orElseGet(() -> {
                    CandidateScreening newScreening = new CandidateScreening();
                    newScreening.setCandidate(candidate);
                    return newScreening;
                });

        // 3. Map foundational primitives cleanly
        screening.setProjectSpecific(Boolean.TRUE.equals(dto.getIsProjectSpecific()));

        // Map engagement configuration safely
        EngagementType engagementInput = EngagementType.FULL_TIME;
        if (dto.getEngagementType() != null) {
            try {
                engagementInput = EngagementType.valueOf(dto.getEngagementType().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid engagement type provided: [{}]. Defaulting to FULL_TIME", dto.getEngagementType());
            }
        }
        screening.setEngagementType(engagementInput);

        screening.setRegion(dto.getRegion());
        screening.setTargetStartDate(dto.getTargetStartDate());
        screening.setProfileSource(dto.getProfileSource());
        String screenedBy = dto.getScreenedBy();
        if ((screenedBy == null || screenedBy.isBlank()) && savedBy != null) {
            screenedBy = savedBy.getFullName();
        }
        screening.setScreenedBy(screenedBy);
        screening.setFeedback(dto.getFeedback());
        screening.setNatureOfRecruitment(dto.getNatureOfRecruitment());
        screening.setRoleStretch(dto.getRoleStretch());
        screening.setSpecialNotes(dto.getSpecialNotes());
        screening.setModifiedAt(Instant.now());

        // 4. Map dependent structures based on conditional workflows
        if (screening.isProjectSpecific()) {
            if (dto.getDepartmentId() == null || dto.getTierId() == null || dto.getDesignationId() == null) {
                throw new IllegalArgumentException("Project infrastructure setups require full structural parameters (Dept, Tier, Role).");
            }

            screening.setProjectName(dto.getProjectName());
            screening.setDepartment(departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found: " + dto.getDepartmentId())));
            screening.setTier(tierRepository.findById(dto.getTierId())
                    .orElseThrow(() -> new EntityNotFoundException("Tier not found: " + dto.getTierId())));
            screening.setDesignation(designationRepository.findById(dto.getDesignationId())
                    .orElseThrow(() -> new EntityNotFoundException("Designation not found: " + dto.getDesignationId())));
        } else {
            screening.setProjectName(null);
            screening.setDepartment(null);
            screening.setTier(null);
            screening.setDesignation(null);
        }

        // Handle Contract Timelines
        screening.setDuration(screening.getEngagementType() != EngagementType.FULL_TIME ? dto.getDuration() : null);

        // Handle Referrer Data
        screening.setReferrerName("Referral".equalsIgnoreCase(dto.getProfileSource()) ? dto.getReferrerName() : null);

        // 5. Final payload constraint validation check
        validateScreeningPayload(screening);

        CandidateScreening savedRecord = screeningRepository.save(screening);

        candidatePipelineAuditService.recordStatusChange(
                candidateId,
                MasterStatus.SCREENING,
                candidate.getStatus(),
                PipelineAuditActionType.SCREENING_SAVED,
                savedBy,
                screenedBy != null && !screenedBy.isBlank() ? "Screened by " + screenedBy : null);

        return ScreeningResponseDTO.fromEntity(savedRecord);
    }

    private void validateScreeningPayload(CandidateScreening screening) {
        if (screening.isProjectSpecific() && (screening.getProjectName() == null || screening.getProjectName().trim().isEmpty())) {
            throw new IllegalArgumentException("Project specific tracking rules require a distinct name assigned.");
        }
        if (screening.getEngagementType() != EngagementType.FULL_TIME && screening.getDuration() == null) {
            throw new IllegalArgumentException("Nonstandard permanent classifications require dynamic contract timeline boundaries.");
        }
    }
}