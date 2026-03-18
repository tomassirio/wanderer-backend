package com.tomassirio.wanderer.command.service;

import java.util.UUID;

/**
 * Service interface for notification command operations.
 *
 * @author tomassirio
 * @since 0.10.0
 */
public interface NotificationService {

    /**
     * Marks a single notification as read.
     *
     * @param userId the ID of the authenticated user (must be the recipient)
     * @param notificationId the ID of the notification to mark as read
     */
    void markAsRead(UUID userId, UUID notificationId);

    /**
     * Marks all notifications for the current user as read.
     *
     * @param userId the ID of the authenticated user
     * @return the number of notifications that were marked as read
     */
    int markAllAsRead(UUID userId);
}
