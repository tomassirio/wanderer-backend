package com.tomassirio.wanderer.commons.dto;

import java.time.Instant;

/**
 * DTO for notification data returned by query endpoints.
 *
 * @since 0.10.0
 */
public record NotificationDTO(
        String id,
        String recipientId,
        String actorId,
        String type,
        String referenceId,
        String message,
        boolean read,
        Instant createdAt) {}

