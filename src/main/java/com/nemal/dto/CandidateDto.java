package com.nemal.dto;

import com.nemal.entity.Candidate;
import com.nemal.enums.MasterStatus;

import java.time.LocalDateTime;
import java.util.List;

public record CandidateDto(
        Long id,
        String name,
        String email,
        String phone,
        Long departmentId,
        String departmentName,
        Long targetDesignationId,
        String targetDesignationName,
        // Tier info derived from the target designation
        Long tierId,
        String tierName,
        Integer tierOrder,
        Integer levelOrder,
        MasterStatus status,
        String resourceRequestNumber,
        String resumeUrl,
        String jdUrl,
        String resourceLink,
        String jobReferenceCode,
        String location,
        String notes,
        Integer yearsOfExperience,
        LocalDateTime appliedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isActive,
        Long coordinatedHrId,
        String coordinatedHrName,
        Long coordinatedHrDepartmentId,
        CandidateClosureDto closure,
        List<CandidateTechnologyDto> technologies
) {
    public static CandidateDto from(Candidate candidate) {
        return from(candidate, null, List.of());
    }

    public static CandidateDto from(Candidate candidate, CandidateClosureDto closure) {
        return from(candidate, closure, List.of());
    }

    public static CandidateDto from(
            Candidate candidate,
            CandidateClosureDto closure,
            List<CandidateTechnologyDto> technologies
    ) {
        var desig = candidate.getTargetDesignation();
        var tier  = (desig != null) ? desig.getTier() : null;

        return new CandidateDto(
                candidate.getId(),
                candidate.getName(),
                candidate.getEmail(),
                candidate.getPhone(),
                candidate.getDepartment() != null ? candidate.getDepartment().getId() : null,
                candidate.getDepartment() != null ? candidate.getDepartment().getName() : null,
                desig != null ? desig.getId() : null,
                desig != null ? desig.getName() : null,
                tier  != null ? tier.getId()        : null,
                tier  != null ? tier.getName()       : null,
                tier  != null ? tier.getTierOrder()  : null,
                desig != null ? desig.getLevelOrder() : null,
                candidate.getStatus(),
                candidate.getResourceRequestNumber(),
                candidate.getResumeUrl(),
                candidate.getJdUrl(),
                candidate.getResourceLink(),
                candidate.getJobReferenceCode(),
                candidate.getLocation(),
                candidate.getNotes(),
                candidate.getYearsOfExperience(),
                candidate.getAppliedAt(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt(),
                candidate.isActive(),
                candidate.getCoordinatedHr() != null ? candidate.getCoordinatedHr().getId() : null,
                candidate.getCoordinatedHr() != null ? candidate.getCoordinatedHr().getFullName().trim() : null,
                candidate.getCoordinatedHr() != null && candidate.getCoordinatedHr().getDepartment() != null
                        ? candidate.getCoordinatedHr().getDepartment().getId()
                        : null,
                closure,
                technologies != null ? technologies : List.of()
        );
    }
}