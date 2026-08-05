package com.nemal.repository;

import com.nemal.entity.EmailDeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, Long> {

    Optional<EmailDeliveryLog> findFirstBySubjectAndBodyAndStatusAndSourceAndSentAtAfterOrderBySentAtDesc(
            String subject,
            String body,
            String status,
            String source,
            LocalDateTime sentAfter
    );

    @Query(
            value = """
                    SELECT
                        MIN(e.id) AS id,
                        e.subject AS subject,
                        e.body AS body,
                        STRING_AGG(DISTINCT e.recipients, ', ' ORDER BY e.recipients) AS recipients,
                        STRING_AGG(DISTINCT NULLIF(TRIM(e.recipient_name), ''), ', '
                                   ORDER BY NULLIF(TRIM(e.recipient_name), '')) AS recipient_name,
                        e.status AS status,
                        MAX(e.error_message) AS error_message,
                        e.source AS source,
                        MAX(e.meeting_link) AS meeting_link,
                        MIN(e.sent_at) AS sent_at,
                        COUNT(*)::bigint AS recipient_count
                    FROM email_delivery_logs e
                    WHERE (CAST(:status AS TEXT) IS NULL OR e.status = CAST(:status AS TEXT))
                      AND (
                           CAST(:search AS TEXT) IS NULL
                           OR e.subject ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                           OR e.recipients ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                           OR COALESCE(e.recipient_name, '') ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                           OR e.body ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                           OR COALESCE(e.meeting_link, '') ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                      )
                    GROUP BY e.subject, e.body, e.status, e.source, date_trunc('minute', e.sent_at)
                    ORDER BY MIN(e.sent_at) DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM (
                        SELECT 1
                        FROM email_delivery_logs e
                        WHERE (CAST(:status AS TEXT) IS NULL OR e.status = CAST(:status AS TEXT))
                          AND (
                               CAST(:search AS TEXT) IS NULL
                               OR e.subject ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                               OR e.recipients ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                               OR COALESCE(e.recipient_name, '') ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                               OR e.body ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                               OR COALESCE(e.meeting_link, '') ILIKE CONCAT('%', CAST(:search AS TEXT), '%')
                          )
                        GROUP BY e.subject, e.body, e.status, e.source, date_trunc('minute', e.sent_at)
                    ) grouped_logs
                    """,
            nativeQuery = true
    )
    Page<EmailDeliveryLogGroupProjection> searchGrouped(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM EmailDeliveryLog e WHERE e.sentAt < :cutoff")
    int deleteBySentAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
