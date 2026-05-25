package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "designation_interview_rules", uniqueConstraints = @UniqueConstraint(columnNames = {"interviewer_designation_id", "candidate_designation_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignationInterviewRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Designation interviewerDesignation;

    @ManyToOne
    private Designation candidateDesignation;

    @Builder.Default
    private boolean allowed = true;
}