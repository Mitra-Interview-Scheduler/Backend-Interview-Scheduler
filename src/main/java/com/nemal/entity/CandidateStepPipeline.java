package com.nemal.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nemal.converter.PipelineStepStatusConverter;
import com.nemal.enums.PipelineStepStatus;
import jakarta.persistence.*;
import lombok.*;
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
    @ToString.Exclude       //  1. Prevents Lombok toString() from crashing on LAZY loading
    @EqualsAndHashCode.Exclude // Prevents infinite loops in equals/hashCode
    @JsonIgnore // 2. Prevents infinite JSON recursion loops
    private Candidate candidate;


    // References master_steps by primary key
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "master_step_id", nullable = false)
    private MasterStep step;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Convert(converter = PipelineStepStatusConverter.class)
    @Column(name = "step_status", nullable = false, length = 20)
    @Builder.Default
    private PipelineStepStatus stepStatus = PipelineStepStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
