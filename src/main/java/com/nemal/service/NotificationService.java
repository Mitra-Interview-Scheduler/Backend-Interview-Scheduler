package com.nemal.service;

import com.nemal.dto.InterviewRequestDto;
import com.nemal.dto.NotificationDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.InterviewPanel;
import com.nemal.entity.InterviewPostponeRequest;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.Notification;
import com.nemal.entity.User;
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
    private final EmailNotificationService emailNotificationService;
    private final int retentionDays;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a");

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate,
            EmailNotificationService emailNotificationService,
            @Value("${notification.retention.days:15}") int retentionDays
    ) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.emailNotificationService = emailNotificationService;
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

        deliver(Notification.builder()
                .recipient(request.getAssignedInterviewer())
                .subject("Interview Scheduled")
                .message(buildInterviewDetailsMessage(
                        "An interview has been scheduled for you. Please review the details below.",
                        request.getCandidateName(),
                        formatDateTime(request.getPreferredStartDateTime()),
                        resolvePosition(request),
                        null
                ))
                .type("INTERVIEW_SCHEDULED")
                .relatedEntityId(request.getId())
                .relatedEntityType("INTERVIEW_REQUEST")
                .read(false)
                .build());
    }

    public void sendInterviewCancelledNotification(InterviewRequest request) {
        if (request.getAssignedInterviewer() != null) {
            deliver(Notification.builder()
                    .recipient(request.getAssignedInterviewer())
                    .subject("Interview Cancelled")
                    .message(buildInterviewDetailsMessage(
                            "The interview below has been cancelled by HR.",
                            request.getCandidateName(),
                            formatDateTime(request.getPreferredStartDateTime()),
                            resolvePosition(request),
                            null
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

        deliver(Notification.builder()
                .recipient(request.getAssignedInterviewer())
                .subject("Interview Reminder")
                .message(buildInterviewDetailsMessage(
                        "This is a reminder for your upcoming interview.",
                        request.getCandidateName(),
                        formatDateTime(request.getPreferredStartDateTime()),
                        resolvePosition(request),
                        null
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

        String interviewerName = request.getAssignedInterviewer() != null
                ? request.getAssignedInterviewer().getFullName()
                : null;

        deliver(Notification.builder()
                .recipient(request.getInterviewCoordinator())
                .subject("Interview Coordinator Assignment")
                .message(buildInterviewDetailsMessage(
                        "You have been added as the interview coordinator. Please review the details below.",
                        request.getCandidateName(),
                        formatDateTime(request.getPreferredStartDateTime()),
                        resolvePosition(request),
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

        deliver(Notification.builder()
                .recipient(panel.getInterviewCoordinator())
                .subject("Interview Coordinator Assignment")
                .message(buildInterviewDetailsMessage(
                        "You have been added as the interview coordinator for a panel interview.",
                        candidateName,
                        formatDateTime(panel.getStartDateTime()),
                        resolvePanelPosition(panel),
                        null
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
                .message(buildInterviewDetailsMessage(
                        "You have been assigned as the candidate coordinator.",
                        candidate.getName(),
                        null,
                        designation,
                        null
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

        String interviewerName = request.getAssignedInterviewer() != null
                ? request.getAssignedInterviewer().getFullName()
                : null;

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Interview Scheduled")
                .message(buildInterviewDetailsMessage(
                        "An interview has been scheduled for your candidate. Please review the details below.",
                        candidate.getName(),
                        formatDateTime(request.getPreferredStartDateTime()),
                        resolvePosition(request),
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

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Interview Cancelled")
                .message(buildInterviewDetailsMessage(
                        "The interview for your candidate has been cancelled.",
                        candidate.getName(),
                        formatDateTime(request.getPreferredStartDateTime()),
                        resolvePosition(request),
                        null
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

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Panel Interview Scheduled")
                .message(buildInterviewDetailsMessage(
                        "A panel interview has been scheduled for your candidate.",
                        candidateName,
                        formatDateTime(panel.getStartDateTime()),
                        resolvePanelPosition(panel),
                        null
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

        deliver(Notification.builder()
                .recipient(candidate.getCoordinatedHr())
                .subject("Panel Interview Cancelled")
                .message(buildInterviewDetailsMessage(
                        "The panel interview for your candidate has been cancelled.",
                        candidateName,
                        formatDateTime(panel.getStartDateTime()),
                        resolvePanelPosition(panel),
                        null
                ))
                .type("INTERVIEW_CANCELLED")
                .relatedEntityId(panel.getId())
                .relatedEntityType("INTERVIEW_PANEL")
                .read(false)
                .build());
    }

    public void sendInterviewPostponeRequestedNotification(InterviewPostponeRequest postponeRequest) {
        InterviewRequest request = postponeRequest.getInterviewRequest();
        if (request == null && postponeRequest.getInterviewSchedule() != null) {
            request = postponeRequest.getInterviewSchedule().getRequest();
        }
        if (request == null) {
            return;
        }

        String interviewerName = postponeRequest.getRequestedBy() != null
                ? postponeRequest.getRequestedBy().getFullName()
                : (request.getAssignedInterviewer() != null
                        ? request.getAssignedInterviewer().getFullName()
                        : "The interviewer");

        Long submitterId = postponeRequest.getRequestedBy() != null
                ? postponeRequest.getRequestedBy().getId()
                : null;

        String message = buildPostponeDetailsMessage(
                interviewerName + " has proposed a new time for a scheduled interview. Please review the details below.",
                request.getCandidateName(),
                formatDateTime(request.getPreferredStartDateTime()),
                resolvePosition(request),
                interviewerName,
                postponeRequest.getReason(),
                formatPreferredWindow(
                        postponeRequest.getPreferredStartDateTime(),
                        postponeRequest.getPreferredEndDateTime())
        );

        java.util.LinkedHashMap<Long, User> recipients = new java.util.LinkedHashMap<>();
        addPostponeRecipient(recipients, request.getRequestedBy(), submitterId);
        if (request.getCandidate() != null) {
            addPostponeRecipient(recipients, request.getCandidate().getCoordinatedHr(), submitterId);
        }
        addPostponeRecipient(recipients, resolveInterviewCoordinator(request), submitterId);

        for (User recipient : recipients.values()) {
            deliver(Notification.builder()
                    .recipient(recipient)
                    .subject("New Interview Time Proposed")
                    .message(message)
                    .type("INTERVIEW_POSTPONE_REQUESTED")
                    .relatedEntityId(postponeRequest.getId())
                    .relatedEntityType("INTERVIEW_POSTPONE_REQUEST")
                    .read(false)
                    .build());
        }
    }

    private User resolveInterviewCoordinator(InterviewRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getInterviewCoordinator() != null) {
            return request.getInterviewCoordinator();
        }
        if (request.getPanel() != null && request.getPanel().getInterviewCoordinator() != null) {
            return request.getPanel().getInterviewCoordinator();
        }
        return null;
    }

    private void addPostponeRecipient(
            java.util.Map<Long, User> recipients,
            User user,
            Long excludeUserId) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (excludeUserId != null && excludeUserId.equals(user.getId())) {
            return;
        }
        recipients.putIfAbsent(user.getId(), user);
    }

    public void sendInterviewPostponeRejectedNotification(InterviewPostponeRequest postponeRequest) {
        if (postponeRequest.getRequestedBy() == null) {
            return;
        }

        InterviewRequest request = postponeRequest.getInterviewRequest();
        if (request == null && postponeRequest.getInterviewSchedule() != null) {
            request = postponeRequest.getInterviewSchedule().getRequest();
        }

        StringBuilder intro = new StringBuilder(
                "Your request to postpone the interview has been declined by HR.");
        if (postponeRequest.getReviewNotes() != null && !postponeRequest.getReviewNotes().isBlank()) {
            intro.append(" Notes: ").append(postponeRequest.getReviewNotes().trim());
        }

        deliver(Notification.builder()
                .recipient(postponeRequest.getRequestedBy())
                .subject("Interview Postpone Request Declined")
                .message(buildPostponeDetailsMessage(
                        intro.toString(),
                        request != null ? request.getCandidateName() : null,
                        request != null ? formatDateTime(request.getPreferredStartDateTime()) : null,
                        request != null ? resolvePosition(request) : null,
                        postponeRequest.getRequestedBy().getFullName(),
                        postponeRequest.getReason(),
                        null
                ))
                .type("INTERVIEW_POSTPONE_REJECTED")
                .relatedEntityId(postponeRequest.getId())
                .relatedEntityType("INTERVIEW_POSTPONE_REQUEST")
                .read(false)
                .build());
    }

    public void sendInterviewPostponeApprovedNotification(
            InterviewPostponeRequest postponeRequest,
            InterviewRequestDto newInterview) {
        if (postponeRequest.getRequestedBy() == null) {
            return;
        }

        InterviewRequest request = postponeRequest.getInterviewRequest();
        if (request == null && postponeRequest.getInterviewSchedule() != null) {
            request = postponeRequest.getInterviewSchedule().getRequest();
        }

        String newWindow = newInterview != null
                ? formatPreferredWindow(newInterview.preferredStartDateTime(), newInterview.preferredEndDateTime())
                : formatPreferredWindow(
                        postponeRequest.getPreferredStartDateTime(),
                        postponeRequest.getPreferredEndDateTime());

        StringBuilder intro = new StringBuilder(
                "HR accepted your proposed time. The previous interview was cancelled and a new interview was scheduled.");
        if (postponeRequest.getReviewNotes() != null && !postponeRequest.getReviewNotes().isBlank()) {
            intro.append(" Notes: ").append(postponeRequest.getReviewNotes().trim());
        }

        deliver(Notification.builder()
                .recipient(postponeRequest.getRequestedBy())
                .subject("Interview Time Change Accepted")
                .message(buildPostponeDetailsMessage(
                        intro.toString(),
                        request != null ? request.getCandidateName()
                                : (newInterview != null ? newInterview.candidateName() : null),
                        newWindow,
                        request != null ? resolvePosition(request)
                                : (newInterview != null ? newInterview.candidateDesignationName() : null),
                        postponeRequest.getRequestedBy().getFullName(),
                        postponeRequest.getReason(),
                        newWindow
                ))
                .type("INTERVIEW_POSTPONE_APPROVED")
                .relatedEntityId(postponeRequest.getId())
                .relatedEntityType("INTERVIEW_POSTPONE_REQUEST")
                .read(false)
                .build());
    }

    public void sendFeedbackSubmittedNotification(Candidate candidate, User interviewer, String interviewType) {
        User recipient = candidate.getCoordinatedHr();
        if (recipient == null) {
            return;
        }

        String interviewerName = interviewer != null ? interviewer.getFullName() : "An interviewer";
        String roundLabel = (interviewType != null && !interviewType.isBlank()) ? interviewType : "interview";

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
        emailNotificationService.notifyAsync(
                saved.getRecipient().getId(),
                saved.getRecipient().getEmail(),
                saved.getRecipient().getFullName(),
                saved.getSubject(),
                saved.getMessage()
        );
    }

    private String buildInterviewDetailsMessage(
            String intro,
            String candidateName,
            String when,
            String position,
            String interviewerName
    ) {
        StringBuilder message = new StringBuilder();
        if (intro != null && !intro.isBlank()) {
            message.append(intro.trim()).append("\n");
        }
        appendDetail(message, "Candidate", candidateName);
        appendDetail(message, "When", when);
        appendDetail(message, "Position", position);
        appendDetail(message, "Interviewer", interviewerName);
        return message.toString().trim();
    }

    private String buildPostponeDetailsMessage(
            String intro,
            String candidateName,
            String when,
            String position,
            String interviewerName,
            String reason,
            String preferredWindow
    ) {
        StringBuilder message = new StringBuilder();
        if (intro != null && !intro.isBlank()) {
            message.append(intro.trim()).append("\n");
        }
        appendDetail(message, "Candidate", candidateName);
        appendDetail(message, "When", when);
        appendDetail(message, "Position", position);
        appendDetail(message, "Interviewer", interviewerName);
        appendDetail(message, "Reason", reason);
        appendDetail(message, "Preferred time", preferredWindow);
        return message.toString().trim();
    }

    private String formatPreferredWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return formatDateTime(start) + " – " + end.format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    private void appendDetail(StringBuilder message, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!message.isEmpty()) {
            message.append("\n");
        }
        message.append(label).append(": ").append(value.trim());
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMATTER) : null;
    }

    private String resolvePosition(InterviewRequest request) {
        if (request.getCandidateDesignation() != null
                && request.getCandidateDesignation().getName() != null
                && !request.getCandidateDesignation().getName().isBlank()) {
            return request.getCandidateDesignation().getName().trim();
        }
        if (request.getCandidate() != null
                && request.getCandidate().getTargetDesignation() != null
                && request.getCandidate().getTargetDesignation().getName() != null
                && !request.getCandidate().getTargetDesignation().getName().isBlank()) {
            return request.getCandidate().getTargetDesignation().getName().trim();
        }
        return "Not specified";
    }

    private String resolvePanelPosition(InterviewPanel panel) {
        if (panel.getCandidate() != null
                && panel.getCandidate().getTargetDesignation() != null
                && panel.getCandidate().getTargetDesignation().getName() != null
                && !panel.getCandidate().getTargetDesignation().getName().isBlank()) {
            return panel.getCandidate().getTargetDesignation().getName().trim();
        }
        return null;
    }
}
