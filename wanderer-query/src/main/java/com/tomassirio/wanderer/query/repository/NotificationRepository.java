package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    /** Find recent unread notifications for a user (optimized for quick checks) */
    @Query(
            "SELECT n FROM Notification n WHERE n.recipientId = :recipientId AND n.read = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByRecipientId(@Param("recipientId") UUID recipientId);

    /** Batch mark notifications as read (useful for mark all as read operations) */
    @Query("SELECT n FROM Notification n WHERE n.id IN :notificationIds")
    List<Notification> findByIdIn(@Param("notificationIds") List<UUID> notificationIds);
}
