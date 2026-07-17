package com.nemal.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nemal.converter.PipelineAuditActionTypeConverter;
import com.nemal.enums.PipelineAuditActionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_pipeline_status_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CandidatePipelineStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_step_id", nullable = false)
    private MasterStep masterStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_master_step_id")
    private MasterStep previousMasterStep;

    @Convert(converter = PipelineAuditActionTypeConverter.class)
    @Column(name = "action_type", nullable = false, length = 50)
    private PipelineAuditActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
