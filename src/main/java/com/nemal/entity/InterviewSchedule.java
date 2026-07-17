package com.nemal.entity;

import com.nemal.enums.InterviewStatus;
import com.nemal.enums.InterviewType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private InterviewRequest request;

    @ManyToOne
    private User interviewer;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    private String meetingLink;

    private String location;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type")
    private InterviewType interviewType;

    private LocalDateTime completedAt;
}