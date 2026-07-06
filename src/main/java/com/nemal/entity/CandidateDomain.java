package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "candidate_domains",
        uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "domain_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "domain_id", nullable = false)
    private Domain domain;
}
