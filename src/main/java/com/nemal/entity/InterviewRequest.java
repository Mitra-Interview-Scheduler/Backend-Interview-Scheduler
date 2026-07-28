package com.nemal.entity;

import com.nemal.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "interview_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// ─── FIX ────────────────────────────────────────────────────────────────────
// @Data generates hashCode() from ALL fields. When Hibernate adds this entity
// into a HashSet (PersistentSet for InterviewPanel.panelRequests), it calls
// hashCode() → which calls assignedInterviewer.hashCode() (User) → which calls
// User.interviewerTechnologies.hashCode() — but Hibernate is still loading
// that Set right now → ConcurrentModificationException.
//
// Fix: use only `id` for equals/hashCode.
// ────────────────────────────────────────────────────────────────────────────
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"requestedBy", "assignedInterviewer", "candidate",
        "candidateDesignation", "availabilitySlot", "panel",
        "requiredTechnologies", "interviewSchedule"})
@EntityListeners(AuditingEntityListener.class)
public class InterviewRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // The candidate's name (can be freeform or from Candidate record)
    @Column(nullable = false)
    private String candidateName;

    @Column(name = "candidate_invite_email")
    private String candidateInviteEmail;

    // Optional link to Candidate record
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_designation_id")
    private Designation candidateDesignation;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "interview_request_technologies",
            joinColumns = @JoinColumn(name = "interview_request_id"),
            inverseJoinColumns = @JoinColumn(name = "technology_id")
    )
    @Builder.Default
    private Set<Technology> requiredTechnologies = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime preferredStartDateTime;

    @Column(nullable = false)
    private LocalDateTime preferredEndDateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_interviewer_id")
    private User assignedInterviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_coordinator_id")
    private User interviewCoordinator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_slot_id")
    private AvailabilitySlot availabilitySlot;

    // If this request is part of a panel interview
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panel_id")
    private InterviewPanel panel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    private LocalDateTime respondedAt;

    @Column(length = 2000)
    private String responseNotes;

    @Column(nullable = false)
    @Builder.Default
    private boolean isUrgent = false;

    @Column(length = 2000)
    private String notes;

    @OneToOne(mappedBy = "request", fetch = FetchType.LAZY)
    private InterviewSchedule interviewSchedule;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public boolean isUrgent() { return isUrgent; }

    public void setUrgent(boolean urgent) { isUrgent = urgent; }
}