package com.nemal.entity;

import com.nemal.enums.PipelineStepStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_pipeline_steps", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"candidate_id", "sequence_order"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CandidateStepPipeline {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    // References the master CandidateStep statusKey configuration string
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_key", referencedColumnName = "status_key", nullable = false)
    private MasterStep step;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status", nullable = false, length = 20)
    @Builder.Default
    private PipelineStepStatus stepStatus = PipelineStepStatus.PENDING;

    @Column(name = "custom_label", length = 100)
    private String customLabel;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
