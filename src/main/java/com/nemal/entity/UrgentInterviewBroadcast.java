package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "urgent_interview_broadcasts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrgentInterviewBroadcast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String message;

    @ManyToOne
    private Designation candidateDesignation;

    @ManyToOne
    private Department targetDepartment;

    @ManyToMany
    @JoinTable(name = "urgent_interview_broadcasts_technologies",
            joinColumns = @JoinColumn(name = "urgent_interview_broadcast_id"),
            inverseJoinColumns = @JoinColumn(name = "technology_id"))
    @Builder.Default
    private Set<Technology> requiredTechnologies = new HashSet<>();

    private LocalDateTime deadline;

    @OneToMany(mappedBy = "broadcast", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<UrgentInterviewResponse> responses = new HashSet<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
