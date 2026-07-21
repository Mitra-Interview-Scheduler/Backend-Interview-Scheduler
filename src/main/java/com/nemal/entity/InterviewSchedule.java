package com.nemal.entity;

import com.nemal.enums.InterviewStatus;
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

    @Column(name = "google_calendar_event_id")
    private String googleCalendarEventId;

    private String location;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    /** Interview type code (e.g. TECHNICAL, HR, MANAGER). Configurable via InterviewType admin. */
    @Column(name = "interview_type")
    private String interviewType;

    private LocalDateTime completedAt;
}