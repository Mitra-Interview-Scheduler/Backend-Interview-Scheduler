package com.nemal.entity;

import com.nemal.enums.InterviewerFilterMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Admin-configurable interview type (e.g. TECHNICAL, HR, MANAGER, ASSESSMENT).
 * Includes interviewer matching rules resolved at schedule time.
 */
@Entity
@Table(name = "interview_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean isSystem = false;

    @Column(name = "round_status_key", length = 64)
    private String roundStatusKey;

    @Column(name = "cancel_restore_status_key", length = 64)
    private String cancelRestoreStatusKey;

    /**
     * When true (default), booking creates a Google Calendar event with Meet link and attachments.
     * When false, no meeting is created or attached for interviews of this type.
     */
    @Column(name = "create_calendar_meeting", nullable = false)
    @Builder.Default
    private boolean createCalendarMeeting = true;

    /**
     * When true (default), scheduling requires picking an interviewer availability slot.
     * When false (assessment-style), HR can record the activity with a due window and notes
     * without booking an interviewer.
     */
    @Column(name = "requires_interviewer", nullable = false)
    @Builder.Default
    private boolean requiresInterviewer = true;

    // ── Interviewer filter rules ────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "department_filter_mode", nullable = false, length = 32)
    @Builder.Default
    private InterviewerFilterMode departmentFilterMode = InterviewerFilterMode.SAME_AS_CANDIDATE;

    @Column(name = "fixed_department_id")
    private Long fixedDepartmentId;

    /** Null means no minimum years-of-experience filter. */
    @Column(name = "min_years_experience")
    private Integer minYearsExperience;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier_filter_mode", nullable = false, length = 32)
    @Builder.Default
    private InterviewerFilterMode tierFilterMode = InterviewerFilterMode.SAME_AS_CANDIDATE;

    @Column(name = "fixed_min_tier_id")
    private Long fixedMinTierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "designation_filter_mode", nullable = false, length = 32)
    @Builder.Default
    private InterviewerFilterMode designationFilterMode = InterviewerFilterMode.SAME_AS_CANDIDATE;

    @Column(name = "fixed_min_designation_id")
    private Long fixedMinDesignationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_filter_mode", nullable = false, length = 32)
    @Builder.Default
    private InterviewerFilterMode domainFilterMode = InterviewerFilterMode.SAME_AS_CANDIDATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_filter_mode", nullable = false, length = 32)
    @Builder.Default
    private InterviewerFilterMode categoryFilterMode = InterviewerFilterMode.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "technology_filter_mode", nullable = false, length = 32)
    @Builder.Default
    private InterviewerFilterMode technologyFilterMode = InterviewerFilterMode.SAME_AS_CANDIDATE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_type_fixed_domains", joinColumns = @JoinColumn(name = "interview_type_id"))
    @Column(name = "domain_id")
    @Builder.Default
    private Set<Long> fixedDomainIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_type_fixed_categories", joinColumns = @JoinColumn(name = "interview_type_id"))
    @Column(name = "category_id")
    @Builder.Default
    private Set<Long> fixedCategoryIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_type_fixed_technologies", joinColumns = @JoinColumn(name = "interview_type_id"))
    @Column(name = "technology_id")
    @Builder.Default
    private Set<Long> fixedTechnologyIds = new HashSet<>();
}
