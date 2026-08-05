package com.nemal.scheduler;

import com.nemal.service.EmailDeliveryLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryLogRetentionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryLogRetentionScheduler.class);

    private final EmailDeliveryLogService emailDeliveryLogService;

    public EmailDeliveryLogRetentionScheduler(EmailDeliveryLogService emailDeliveryLogService) {
        this.emailDeliveryLogService = emailDeliveryLogService;
    }

    @Scheduled(cron = "${email.log.retention.cron:0 0 12 * * *}")
    public void purgeExpiredEmailLogs() {
        int deleted = emailDeliveryLogService.purgeExpiredLogs();
        if (deleted > 0) {
            logger.info(
                    "Purged {} email delivery log(s) older than {} day(s)",
                    deleted,
                    emailDeliveryLogService.getRetentionDays()
            );
        }
    }
}
