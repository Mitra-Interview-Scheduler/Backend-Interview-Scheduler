package com.nemal.repository;

import java.time.LocalDateTime;

public interface EmailDeliveryLogGroupProjection {
    Long getId();
    String getSubject();
    String getBody();
    String getRecipients();
    String getRecipientName();
    String getStatus();
    String getErrorMessage();
    String getSource();
    String getMeetingLink();
    LocalDateTime getSentAt();
    Long getRecipientCount();
}
