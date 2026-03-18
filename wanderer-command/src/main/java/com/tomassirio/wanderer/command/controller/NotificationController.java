package com.tomassirio.wanderer.command.controller;

import com.tomassirio.wanderer.command.service.NotificationService;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for notification command operations.
 *
 * @author tomassirio
 * @since 0.10.0
 */
@RestController
@RequestMapping(
        value = ApiConstants.NOTIFICATIONS_PATH,
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Endpoints for managing notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @PatchMapping(ApiConstants.NOTIFICATION_READ_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Mark a notification as read",
            description =
                    "Marks a single notification as read. The authenticated user must be the"
                            + " recipient of the notification. Returns 202 Accepted.")
    public ResponseEntity<Void> markAsRead(
            @Parameter(hidden = true) @CurrentUserId UUID userId, @PathVariable UUID id) {
        log.info("Received request to mark notification {} as read by user {}", id, userId);
        notificationService.markAsRead(userId, id);
        log.info("Notification {} marked as read", id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PatchMapping(ApiConstants.NOTIFICATIONS_READ_ALL_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Mark all notifications as read",
            description =
                    "Marks all unread notifications for the current user as read."
                            + " Returns 202 Accepted with the count of notifications marked.")
    public ResponseEntity<Integer> markAllAsRead(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        log.info("Received request to mark all notifications as read for user {}", userId);
        int count = notificationService.markAllAsRead(userId);
        log.info("Marked {} notifications as read for user {}", count, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(count);
    }
}
