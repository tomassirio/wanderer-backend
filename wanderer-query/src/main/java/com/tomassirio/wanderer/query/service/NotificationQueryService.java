package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.commons.dto.NotificationDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for querying notifications.
 *
 * @author tomassirio
 * @since 0.10.0
 */
public interface NotificationQueryService {

    /**
     * Retrieves a paginated list of notifications for a user.
     *
     * @param recipientId the ID of the authenticated user
     * @param pageable pagination parameters
     * @return paginated notifications ordered by creation date descending
     */
    Page<NotificationDTO> getNotifications(UUID recipientId, Pageable pageable);

    /**
     * Returns the count of unread notifications for a user.
     *
     * @param recipientId the ID of the authenticated user
     * @return unread notification count
     */
    long getUnreadCount(UUID recipientId);
}
