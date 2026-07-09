package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "interview_panels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// ─── FIX ────────────────────────────────────────────────────────────────────
// Same root cause as InterviewRequest / User. @Data's hashCode() would touch
// panelRequests (a lazy Set) during loading → crash. Use id-only hashCode.
// ────────────────────────────────────────────────────────────────────────────
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"panelRequests", "requestedBy", "candidate"})
@EntityListeners(AuditingEntityListener.class)
public class InterviewPanel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Column(nullable = false)
    private String candidateName;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    @Column(length = 2000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_coordinator_id")
    private User interviewCoordinator;

    @Column(nullable = false)
    @Builder.Default
    private boolean isUrgent = false;

    @Column(name = "google_calendar_event_id")
    private String googleCalendarEventId;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @OneToMany(mappedBy = "panel", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<InterviewRequest> panelRequests = new HashSet<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public boolean isUrgent() { return isUrgent; }

    public void setUrgent(boolean urgent) { isUrgent = urgent; }
}