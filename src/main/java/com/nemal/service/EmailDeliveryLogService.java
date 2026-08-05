package com.nemal.service;

import com.nemal.dto.EmailDeliveryLogDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.entity.EmailDeliveryLog;
import com.nemal.repository.EmailDeliveryLogGroupProjection;
import com.nemal.repository.EmailDeliveryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EmailDeliveryLogService {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String SOURCE_NOTIFICATION = "NOTIFICATION";
    public static final String SOURCE_CALENDAR_INVITE = "CALENDAR_INVITE";

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryLogService.class);
    private static final int GROUP_MERGE_WINDOW_MINUTES = 2;

    private final EmailDeliveryLogRepository emailDeliveryLogRepository;
    private final int retentionDays;

    public EmailDeliveryLogService(
            EmailDeliveryLogRepository emailDeliveryLogRepository,
            @Value("${email.log.retention.days:15}") int retentionDays
    ) {
        this.emailDeliveryLogRepository = emailDeliveryLogRepository;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public void logNotificationDelivery(
            String recipients,
            String recipientName,
            String subject,
            String body,
            String status,
            String errorMessage
    ) {
        logDelivery(recipients, recipientName, subject, body, status, errorMessage, SOURCE_NOTIFICATION, null);
    }

    @Transactional
    public void logCalendarInvite(
            String recipients,
            String recipientName,
            String subject,
            String body,
            String meetingLink,
            String status,
            String errorMessage
    ) {
        logDelivery(
                recipients,
                recipientName,
                subject,
                body,
                status,
                errorMessage,
                SOURCE_CALENDAR_INVITE,
                meetingLink
        );
    }

    @Transactional
    public void logDelivery(
            String recipients,
            String recipientName,
            String subject,
            String body,
            String status,
            String errorMessage,
            String source,
            String meetingLink
    ) {
        try {
            String safeRecipients = recipients != null ? recipients.trim() : "";
            String safeSubject = subject != null ? subject : "";
            String safeBody = body != null ? body : "";
            String safeStatus = status != null ? status : STATUS_SENT;
            String safeSource = source != null && !source.isBlank()
                    ? source.trim().toUpperCase(Locale.ROOT)
                    : SOURCE_NOTIFICATION;
            String safeRecipientName = blankToNull(recipientName);
            String safeError = truncate(errorMessage, 2000);
            String safeMeetingLink = blankToNull(meetingLink);

            if (safeRecipients.isBlank() && STATUS_SENT.equals(safeStatus)) {
                return;
            }

            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(GROUP_MERGE_WINDOW_MINUTES);
            Optional<EmailDeliveryLog> recent = emailDeliveryLogRepository
                    .findFirstBySubjectAndBodyAndStatusAndSourceAndSentAtAfterOrderBySentAtDesc(
                            safeSubject, safeBody, safeStatus, safeSource, windowStart);

            if (recent.isPresent()) {
                EmailDeliveryLog existing = recent.get();
                existing.setRecipients(mergeCsv(existing.getRecipients(), safeRecipients));
                existing.setRecipientName(mergeCsv(existing.getRecipientName(), safeRecipientName));
                if (safeMeetingLink != null) {
                    existing.setMeetingLink(safeMeetingLink);
                }
                if (safeError != null && (existing.getErrorMessage() == null || existing.getErrorMessage().isBlank())) {
                    existing.setErrorMessage(safeError);
                }
                emailDeliveryLogRepository.save(existing);
                return;
            }

            emailDeliveryLogRepository.save(EmailDeliveryLog.builder()
                    .recipients(safeRecipients)
                    .recipientName(safeRecipientName)
                    .subject(safeSubject)
                    .body(safeBody)
                    .status(safeStatus)
                    .errorMessage(safeError)
                    .source(safeSource)
                    .meetingLink(safeMeetingLink)
                    .sentAt(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            logger.warn("Failed to persist email delivery log: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponseDto<EmailDeliveryLogDto> list(String search, String status, int page, int size) {
        int pageValue = Math.max(0, page);
        int sizeValue = Math.min(100, Math.max(1, size));
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String statusFilter = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                ? status.trim().toUpperCase(Locale.ROOT)
                : null;

        Pageable pageable = PageRequest.of(pageValue, sizeValue);
        Page<EmailDeliveryLogGroupProjection> result =
                emailDeliveryLogRepository.searchGrouped(searchTerm, statusFilter, pageable);

        return new PaginatedResponseDto<>(
                result.getContent().stream().map(EmailDeliveryLogDto::fromGroup).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public EmailDeliveryLogDto getById(Long id) {
        return emailDeliveryLogRepository.findById(id)
                .map(EmailDeliveryLogDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Email delivery log not found: " + id));
    }

    @Transactional
    public int purgeExpiredLogs() {
        return emailDeliveryLogRepository.deleteBySentAtBefore(LocalDateTime.now().minusDays(retentionDays));
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String mergeCsv(String existing, String addition) {
        Set<String> values = new LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            Arrays.stream(existing.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(values::add);
        }
        if (addition != null && !addition.isBlank()) {
            Arrays.stream(addition.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(values::add);
        }
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
