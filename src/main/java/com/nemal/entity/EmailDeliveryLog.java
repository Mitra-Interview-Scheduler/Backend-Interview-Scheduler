package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_delivery_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Comma-separated recipient email addresses. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipients;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** NOTIFICATION (SMTP) or CALENDAR_INVITE (Google Calendar). */
    @Column(nullable = false, length = 64)
    @Builder.Default
    private String source = "NOTIFICATION";

    @Column(name = "meeting_link", columnDefinition = "TEXT")
    private String meetingLink;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
        if (source == null || source.isBlank()) {
            source = "NOTIFICATION";
        }
    }
}
