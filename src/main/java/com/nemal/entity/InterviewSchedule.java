package com.nemal.entity;

import com.nemal.enums.AssessmentPhase;
import com.nemal.enums.InterviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** Assessment workflow phase; null for live interviewer bookings. */
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_phase", length = 32)
    private AssessmentPhase assessmentPhase;

    @Column(name = "assessment_file_name", length = 255)
    private String assessmentFileName;

    @Column(name = "assessment_content_type", length = 150)
    private String assessmentContentType;

    @Column(name = "assessment_file_size")
    private Long assessmentFileSize;

    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "assessment_file_data", columnDefinition = "bytea")
    private byte[] assessmentFileData;

    @Column(name = "assessment_uploaded_at")
    private LocalDateTime assessmentUploadedAt;
}