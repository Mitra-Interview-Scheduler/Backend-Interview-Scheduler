package com.nemal.dto;

import com.nemal.entity.Notification;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String subject,
        String message,
        String type,
        Long relatedEntityId,
        String relatedEntityType,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getSubject(),
                notification.getMessage(),
                notification.getType(),
                notification.getRelatedEntityId(),
                notification.getRelatedEntityType(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
