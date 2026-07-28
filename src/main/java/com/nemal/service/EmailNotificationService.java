package com.nemal.service;

import com.nemal.entity.UserSettings;
import com.nemal.repository.UserSettingsRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailNotificationService {

    private final UserSettingsRepository userSettingsRepository;
    private final EmailService emailService;

    public EmailNotificationService(UserSettingsRepository userSettingsRepository, EmailService emailService) {
        this.userSettingsRepository = userSettingsRepository;
        this.emailService = emailService;
    }

    @Async
    @Transactional(readOnly = true)
    public void notifyAsync(Long recipientId, String recipientEmail, String recipientName, String subject, String message) {
        boolean enabled = userSettingsRepository.findByUserId(recipientId)
                .map(UserSettings::isEmailNotificationsEnabled)
                .orElse(true);

        if (!enabled) {
            return;
        }

        emailService.sendNotificationEmail(recipientEmail, recipientName, subject, message);
    }
}
