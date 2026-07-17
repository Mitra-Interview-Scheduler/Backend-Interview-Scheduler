package com.nemal.entity;

import com.nemal.enums.EngagementType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "candidate_screenings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateScreening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;

    @Column(name = "is_project_specific")
    @Builder.Default
    private boolean isProjectSpecific = false;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "region")
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_type", length = 50, nullable = false)
    @Builder.Default
    private EngagementType engagementType = EngagementType.FULL_TIME; // Default fallback initialization


    @Column(name = "duration")
    private Integer duration;

    @Column(name = "target_start_date")
    private String targetStartDate;

    @Column(name = "profile_source", length = 100)
    private String profileSource;

    @Column(name = "referrer_name")
    private String referrerName;

    @Column(name = "screened_by")
    private String screenedBy;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "nature_of_recruitment", length = 100)
    private String natureOfRecruitment;

    @Column(name = "role_stretch", length = 50)
    private String roleStretch;

    @Column(name = "special_notes", columnDefinition = "TEXT")
    private String specialNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private Tier tier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation; // Project Role field in UI

    @Column(name = "modified_at")
    private Instant modifiedAt;
}