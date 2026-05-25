package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_schedule_id", nullable = false)
    private InterviewSchedule interviewSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interviewer_id", nullable = false)
    private User interviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private FeedbackForm form;

    @Type(JsonBinaryType.class)
    @Column(name = "responses_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode responsesJson;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (submittedAt == null) submittedAt = now;
        if (responsesJson == null) responsesJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
