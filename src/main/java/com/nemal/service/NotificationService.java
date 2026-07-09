package com.nemal.service;

import com.nemal.dto.NotificationDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.InterviewPanel;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.Notification;
import com.nemal.entity.User;
import com.nemal.enums.InterviewType;
import com.nemal.enums.MasterStatus;
import com.nemal.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final int retentionDays;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a");

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate,
            @Value("${notification.retention.days:30}") int retentionDays
    ) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.retentionDays = Math.max(1, retentionDays);
    }

    private LocalDateTime retentionCutoff() {
        return LocalDateTime.now().minusDays(retentionDays);
    }

    @Transactional
    public int purgeExpiredNotifications() {
        return notificationRepository.deleteByCreatedAtBefore(retentionCutoff());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getMyNotifications(User user) {
        return notificationRepository
                .findByRecipientIdAndCreatedAtAfterOrderByCreatedAtDesc(user.getId(), retentionCutoff())
                .stream()
                .limit(50)
                .map(NotificationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        return notificationRepository.countByRecipientIdAndReadFalseAndCreatedAtAfter(
                user.getId(),
                retentionCutoff()
        );
    }

    @Transactional
    public NotificationDto markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, user.getId())
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }

        return NotificationDto.from(notification);
    }

    @Transactional
    public int markAllAsRead(User user) {
        LocalDateTime cutoff = retentionCutoff();
        List<Notification> unread = notificationRepository
                .findByRecipientIdAndReadFalseOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(notification -> notification.getCreatedAt() != null
                        && notification.getCreatedAt().isAfter(cutoff))
                .toList();

        if (unread.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        unread.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(now);
        });
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    public void sendInterviewScheduledNotification(InterviewRequest request) {
        if (request.getAssignedInterviewer() == null) {
            return;
        }

        String formattedDateTime = request.getPreferredStartDateTime().format(DATE_FORMATTER);

        deliver(Notification.builder()
                .recipient(request.getAssignedInterviewer())
                .subject("Interview Scheduled")
                .message(String.format(
                        "An interview has been scheduled for you with candidate %s on %s. " +
                                "Position: %s. Please check your schedule.",
                        request.getCandidateName(),
                        formattedDateTime,
                        request.getCandidateDesignation() != null
                                ? request.getCandidateDesignation().getName()
                                : "Not specified"
                ))
                .type("INTERVIEW_SCHEDULED")
                .relatedEntityId(request.getId())
                .relatedEntityType("INTERVIEW_REQUEST")
                .read(false)
                .build());
    }

    public void sendInterviewCancelledNotification(InterviewRequest request) {
        if (request.getAssignedInterviewer() != null) {
            String formattedDateTime = request.getPreferredStartDateTime().format(DATE_FORMATTER);

            deliver(Notification.builder()
                    .recipient(request.getAssignedInterviewer())
                    .subject("Interview Cancelled")
                    .message(String.format(
                            "The interview with candidate %s scheduled for %s has been cancelled by HR.",
                            request.getCandidateName(),
                            formattedDateTime
                    ))
                    .type("INTERVIEW_CANCELLED")
                    .relatedEntityId(request.getId())
                    .relatedEntityType("INTERVIEW_REQUEST")
                    .read(false)
                    .build());
        }
    }

    public void sendInterviewReminderNotification(InterviewRequest request) {
        if (request.getAssignedInterviewer() == null) {
            return;
        }

        String formattedDateTime = request.getPreferredStartDateTime().format(DATE_FORMATTER);

        deliver(Notification.builder()
                .recipient(request.getAssignedInterviewer())
                .subject("Interview Reminder")
                .message(String.format(
                        "Reminder: You have an interview with %s scheduled for %s.",
                        request.getCandidateName(),
                        formattedDateTime
                ))
                .type("INTERVIEW_REMINDER")
                .relatedEntityId(request.getId())
                .relatedEntityType("INTERVIEW_REQUEST")
                .read(false)
                .build());
    }

    public void sendInterviewCoordinatorScheduledNotification(InterviewRequest request) {
        if (request.getInterviewCoordinator() == null) {
            return;
        }

        String formattedDateTime = request.getPreferredStartDateTime().format(DATE_FORMATTER);
        String interviewerName = request.getAssignedInterviewer() != null
                ? request.getAssignedInterviewer().getFullName()
                : "the assigned interviewer";

        deliver(Notification.builder()
                .recipient(request.getInterviewCoordinator())
                .subject("Interview Coordinator Assignment")
                .message(String.format(
                        "You have been added as the interview coordinator for candidate %s. " +
                                "The interview is scheduled on %s with %s.",
                        request.getCandidateName(),
                        formattedDateTime,
                        interviewerName
                ))
                .type("INTERVIEW_COORDINATOR_ASSIGNED")
                .relatedEntityId(request.getId())
                .relatedEntityType("INTERVIEW_REQUEST")
                .read(false)
                .build());
    }

    public void sendInterviewCoordinatorPanelScheduledNotification(InterviewPanel panel, String candidateName) {
        if (panel.getInterviewCoordinator() == null) {
            return;
        }

        String formattedDateTime = panel.getStartDateTime().format(DATE_FORMATTER);

        deliver(Notification.builder()
                .recipient(panel.getInterviewCoordinator())
                .subject("Interview Coordinator Assignment")
                .message(String.format(
                        "You have been added as the interview coordinator for candidate %s. " +
                                "The panel interview is scheduled on %s.",
                        candidateName,
                        formattedDateTime
                ))
                .type("INTERVIEW_COORDINATOR_ASSIGNED")
                .relatedEntityId(panel.getId())
                .relatedEntityType("INTERVIEW_PANEL")
                .read(false)
                .build());
    }

    public void sendCandidateCoordinatorAssignedNotification(Candidate candidate) {
        User recipient = candidate.getCoordinatedHr();
        if (recipient == null) {
            return;
        }

        String designation = candidate.getTargetDesignation() != null
                ? candidate.getTargetDesignation().getName()
                : "Not specified";

        deliver(Notification.builder()
                .recipient(recipient)
                .subject("Candidate Coordinator Assignment")
                .message(String.format(
                        "You have been assigned as the candidate coordinator for %s (%s).",
                        candidate.getName(),
                        designation
                ))
                .type("CANDIDATE_COORDINATOR_ASSIGNED")
                .relatedEntityId(candidate.getId())
                .relatedEntityType("CANDIDATE")
                .read(false)
                .build());
    }

    public void sendCoordinatedHrInterviewScheduledNotification(InterviewRequest request) {
        Candidate candidate = request.getCandidate();
        if (candidate == null || candidate.getCoordinatedHr() == null) {
            return;
        }

        String formattedDateTime = request.getPreferredStartDateTime().format(DATE_FORMATTER);
        String interviewerName = request.getAssignedInterviewer() != null
                ? request.getAssignedInterviewer().getFullName()
                : "the assigned interviewer";

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Interview Scheduled")
                .message(String.format(
                        "An interview has been scheduled for your candidate %s on %s with %s.",
                        candidate.getName(),
                        formattedDateTime,
                        interviewerName
                ))
                .type("INTERVIEW_SCHEDULED")
                .relatedEntityId(request.getId())
                .relatedEntityType("INTERVIEW_REQUEST")
                .read(false)
                .build());
    }

    public void sendCoordinatedHrInterviewCancelledNotification(InterviewRequest request) {
        Candidate candidate = request.getCandidate();
        if (candidate == null || candidate.getCoordinatedHr() == null) {
            return;
        }

        String formattedDateTime = request.getPreferredStartDateTime().format(DATE_FORMATTER);

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Interview Cancelled")
                .message(String.format(
                        "The interview for your candidate %s scheduled on %s has been cancelled.",
                        candidate.getName(),
                        formattedDateTime
                ))
                .type("INTERVIEW_CANCELLED")
                .relatedEntityId(request.getId())
                .relatedEntityType("INTERVIEW_REQUEST")
                .read(false)
                .build());
    }

    public void sendCoordinatedHrPanelInterviewScheduledNotification(InterviewPanel panel, String candidateName) {
        Candidate candidate = panel.getCandidate();
        if (candidate == null || candidate.getCoordinatedHr() == null) {
            return;
        }

        String formattedDateTime = panel.getStartDateTime().format(DATE_FORMATTER);

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Panel Interview Scheduled")
                .message(String.format(
                        "A panel interview has been scheduled for your candidate %s on %s.",
                        candidateName,
                        formattedDateTime
                ))
                .type("INTERVIEW_SCHEDULED")
                .relatedEntityId(panel.getId())
                .relatedEntityType("INTERVIEW_PANEL")
                .read(false)
                .build());
    }

    public void sendCoordinatedHrPanelInterviewCancelledNotification(InterviewPanel panel, String candidateName) {
        Candidate candidate = panel.getCandidate();
        if (candidate == null || candidate.getCoordinatedHr() == null) {
            return;
        }

        String formattedDateTime = panel.getStartDateTime().format(DATE_FORMATTER);

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Panel Interview Cancelled")
                .message(String.format(
                        "The panel interview for your candidate %s scheduled on %s has been cancelled.",
                        candidateName,
                        formattedDateTime
                ))
                .type("INTERVIEW_CANCELLED")
                .relatedEntityId(panel.getId())
                .relatedEntityType("INTERVIEW_PANEL")
                .read(false)
                .build());
    }

    public void sendFeedbackSubmittedNotification(Candidate candidate, User interviewer, InterviewType interviewType) {
        User recipient = candidate.getCoordinatedHr();
        if (recipient == null) {
            return;
        }

        String interviewerName = interviewer != null ? interviewer.getFullName() : "An interviewer";
        String roundLabel = interviewType != null ? interviewType.name() : "interview";

        deliver(Notification.builder()
                .recipient(recipient)
                .subject("Feedback Submitted")
                .message(String.format(
                        "%s submitted %s feedback for candidate %s.",
                        interviewerName,
                        roundLabel,
                        candidate.getName()
                ))
                .type("FEEDBACK_SUBMITTED")
                .relatedEntityId(candidate.getId())
                .relatedEntityType("CANDIDATE")
                .read(false)
                .build());
    }

    public void sendCandidateStatusChangedNotification(
            Candidate candidate,
            MasterStatus oldStatus,
            MasterStatus newStatus,
            User changedBy
    ) {
        User recipient = candidate.getCoordinatedHr();
        if (recipient == null) {
            return;
        }
        if (changedBy != null && recipient.getId().equals(changedBy.getId())) {
            return;
        }

        String changerName = changedBy != null ? changedBy.getFullName() : "Someone";
        String oldLabel = oldStatus != null ? oldStatus.name() : "unknown";
        String newLabel = newStatus != null ? newStatus.name() : "unknown";

        deliver(Notification.builder()
                .recipient(recipient)
                .subject("Candidate Status Updated")
                .message(String.format(
                        "%s moved candidate %s from %s to %s.",
                        changerName,
                        candidate.getName(),
                        oldLabel,
                        newLabel
                ))
                .type("STATUS_CHANGED")
                .relatedEntityId(candidate.getId())
                .relatedEntityType("CANDIDATE")
                .read(false)
                .build());
    }

    private void deliver(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        NotificationDto dto = NotificationDto.from(saved);
        messagingTemplate.convertAndSendToUser(
                saved.getRecipient().getEmail(),
                "/queue/notifications",
                dto
        );
    }
}
