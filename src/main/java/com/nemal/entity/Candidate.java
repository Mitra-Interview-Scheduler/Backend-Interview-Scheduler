package com.nemal.entity;

import com.nemal.enums.MasterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Candidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Stored lowercase-trimmed; global uniqueness enforced in CandidateService
    @Column(unique = true, nullable = false)
    private String email;

    @Column( nullable = false)
    private String phone;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "target_designation_id")
    private Designation targetDesignation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MasterStatus status = MasterStatus.NEW;

    @Column(length = 2000)
    private String resumeUrl;

    /** Job Description URL (link to the role's JD page) */
    @Column(name = "jd_url", length = 2000)
    private String jdUrl;

    /** Internal requisition / job reference code, e.g. REQ-2024-001 */
    @Column(name = "job_reference_code", length = 100)
    private String jobReferenceCode;

    /** Candidate's current location / city */
    @Column(length = 255)
    private String location;

    @Column(length = 2000)
    private String notes;

    private Integer yearsOfExperience;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "resource_request_number")
    private String resourceRequestNumber;

    @Column(name = "resource_link", length = 2000)
    private String resourceLink;

    @PrePersist
    protected void onCreate() {
        if (appliedAt == null) {
            appliedAt = LocalDateTime.now();
        }
    }
}
