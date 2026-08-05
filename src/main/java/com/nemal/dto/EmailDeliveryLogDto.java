package com.nemal.dto;

import com.nemal.entity.EmailDeliveryLog;
import com.nemal.repository.EmailDeliveryLogGroupProjection;

import java.time.LocalDateTime;

public record EmailDeliveryLogDto(
        Long id,
        String subject,
        String body,
        String recipients,
        String recipientName,
        String status,
        String errorMessage,
        String source,
        String meetingLink,
        boolean hasMeetingLink,
        LocalDateTime sentAt,
        long recipientCount
) {
    public static EmailDeliveryLogDto from(EmailDeliveryLog log) {
        return new EmailDeliveryLogDto(
                log.getId(),
                log.getSubject(),
                log.getBody(),
                log.getRecipients(),
                log.getRecipientName(),
                log.getStatus(),
                log.getErrorMessage(),
                log.getSource(),
                log.getMeetingLink(),
                hasLink(log.getMeetingLink()),
                log.getSentAt(),
                1L
        );
    }

    public static EmailDeliveryLogDto fromGroup(EmailDeliveryLogGroupProjection group) {
        return new EmailDeliveryLogDto(
                group.getId(),
                group.getSubject(),
                group.getBody(),
                group.getRecipients(),
                group.getRecipientName(),
                group.getStatus(),
                group.getErrorMessage(),
                group.getSource(),
                group.getMeetingLink(),
                hasLink(group.getMeetingLink()),
                group.getSentAt(),
                group.getRecipientCount() != null ? group.getRecipientCount() : 1L
        );
    }

    private static boolean hasLink(String meetingLink) {
        return meetingLink != null && !meetingLink.isBlank();
    }
}
