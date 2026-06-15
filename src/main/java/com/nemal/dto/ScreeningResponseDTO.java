package com.nemal.dto;

import com.nemal.entity.CandidateScreening;
import com.nemal.enums.EngagementType;
import lombok.Builder;
import java.time.Instant;

@Builder
public record ScreeningResponseDTO(
        Long id,
        Long candidateId,
        boolean isProjectSpecific,
        String projectName,
        String region,
        EngagementType engagementType,
        Integer duration,
        String targetStartDate,
        String profileSource,
        String referrerName,
        String screenedBy,
        String feedback,
        String natureOfRecruitment,
        String roleStretch,
        String specialNotes,
        Long departmentId,
        Long tierId,
        Long designationId,
        Instant modifiedAt
) {

    public static ScreeningResponseDTO fromEntity(CandidateScreening s) {
        if (s == null) {
            return null;
        }

        return ScreeningResponseDTO.builder()
                .id(s.getId())
                .candidateId(s.getCandidate() != null ? s.getCandidate().getId() : null)
                .isProjectSpecific(s.isProjectSpecific())
                .projectName(s.getProjectName())
                .region(s.getRegion())
                .engagementType(s.getEngagementType())
                .duration(s.getDuration())
                .targetStartDate(s.getTargetStartDate())
                .profileSource(s.getProfileSource())
                .referrerName(s.getReferrerName())
                .screenedBy(s.getScreenedBy())
                .feedback(s.getFeedback())
                .natureOfRecruitment(s.getNatureOfRecruitment())
                .roleStretch(s.getRoleStretch())
                .specialNotes(s.getSpecialNotes())
                .departmentId(s.getDepartment() != null ? s.getDepartment().getId() : null)
                .tierId(s.getTier() != null ? s.getTier().getId() : null)
                .designationId(s.getDesignation() != null ? s.getDesignation().getId() : null)
                .modifiedAt(s.getModifiedAt())
                .build();
    }
}