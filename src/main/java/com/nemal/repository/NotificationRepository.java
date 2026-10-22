package com.nemal.repository;

import com.nemal.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long recipientId,
            LocalDateTime createdAfter
    );

    long countByRecipientIdAndReadFalse(Long recipientId);

    long countByRecipientIdAndReadFalseAndCreatedAtAfter(Long recipientId, LocalDateTime createdAfter);

    java.util.Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    boolean existsByRelatedEntityTypeAndRelatedEntityIdAndType(
            String relatedEntityType,
            Long relatedEntityId,
            String type
    );

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    long countByCreatedAtAfter(LocalDateTime createdAfter);

    @Query("""
            SELECT n.type, COUNT(n)
            FROM Notification n
            WHERE n.createdAt >= :from
            GROUP BY n.type
            ORDER BY COUNT(n) DESC
            """)
    List<Object[]> countByTypeSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipient.id = :recipientId
              AND n.type = :type
              AND n.relatedEntityId = :relatedEntityId
              AND n.read = false
            """)
    long countUnreadByRecipientTypeAndEntity(
            @Param("recipientId") Long recipientId,
            @Param("type") String type,
            @Param("relatedEntityId") Long relatedEntityId);
}