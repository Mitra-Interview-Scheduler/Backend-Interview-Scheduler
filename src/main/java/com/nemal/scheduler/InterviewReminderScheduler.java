package com.nemal.scheduler;

import com.nemal.entity.InterviewRequest;
import com.nemal.repository.InterviewRequestRepository;
import com.nemal.repository.NotificationRepository;
import com.nemal.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class InterviewReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(InterviewReminderScheduler.class);

    private final InterviewRequestRepository interviewRequestRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final int leadMinutes;

    public InterviewReminderScheduler(
            InterviewRequestRepository interviewRequestRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            @Value("${notification.reminder.lead-minutes:60}") int leadMinutes
    ) {
        this.interviewRequestRepository = interviewRequestRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.leadMinutes = leadMinutes;
    }

    @Scheduled(cron = "${notification.reminder.cron:0 */15 * * * *}")
    public void sendUpcomingInterviewReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusMinutes(leadMinutes);

        List<InterviewRequest> upcoming = interviewRequestRepository
                .findAcceptedInterviewsStartingBetween(now, windowEnd);

        for (InterviewRequest request : upcoming) {
            boolean alreadySent = notificationRepository.existsByRelatedEntityTypeAndRelatedEntityIdAndType(
                    "INTERVIEW_REQUEST",
                    request.getId(),
                    "INTERVIEW_REMINDER"
            );
            if (!alreadySent) {
                notificationService.sendInterviewReminderNotification(request);
                logger.info("Sent interview reminder for request {}", request.getId());
            }
        }
    }
}
