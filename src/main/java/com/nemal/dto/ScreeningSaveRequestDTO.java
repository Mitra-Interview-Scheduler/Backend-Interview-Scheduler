package com.nemal.dto;

import com.nemal.entity.User;
import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningSaveRequestDTO {
    private Boolean isProjectSpecific;
    private String projectName;
    private String region;
    private String engagementType;
    private Integer duration;
    private String targetStartDate;
    private String profileSource;
    private String referrerName;
    private String screenedBy;
    private String feedback;
    private String natureOfRecruitment;
    private String roleStretch;
    private String specialNotes;
    private Long departmentId;
    private Long tierId;
    private Long designationId;
    private Instant modifiedAt;
}