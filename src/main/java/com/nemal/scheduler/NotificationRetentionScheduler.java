package com.nemal.scheduler;

import com.nemal.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetentionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRetentionScheduler.class);

    private final NotificationService notificationService;

    public NotificationRetentionScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${notification.retention.cron:0 0 2 * * *}")
    public void purgeExpiredNotifications() {
        int deleted = notificationService.purgeExpiredNotifications();
        if (deleted > 0) {
            logger.info("Purged {} notification(s) older than retention window", deleted);
        }
    }
}
