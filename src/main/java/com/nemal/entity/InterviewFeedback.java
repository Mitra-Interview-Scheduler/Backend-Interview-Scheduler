package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private InterviewSchedule schedule;

    private int technicalScore;

    private int communicationScore;

    private String comments;

    private boolean recommended;

    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();
}