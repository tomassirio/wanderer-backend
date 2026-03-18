package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.NotificationDTO;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import com.tomassirio.wanderer.query.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for querying notifications.
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
@Tag(name = "Notifications Query", description = "Endpoints for querying notifications")
public class NotificationQueryController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping(ApiConstants.NOTIFICATIONS_ME_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get my notifications",
            description =
                    "Retrieves a paginated list of notifications for the current user,"
                            + " ordered by creation date descending.")
    public ResponseEntity<Page<NotificationDTO>> getMyNotifications(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info("Received request to get notifications for user {}", userId);
        Page<NotificationDTO> notifications =
                notificationQueryService.getNotifications(userId, pageable);
        log.info(
                "Retrieved {} notifications for user {}", notifications.getTotalElements(), userId);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping(ApiConstants.NOTIFICATIONS_UNREAD_COUNT_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get unread notification count",
            description = "Returns the number of unread notifications for the current user.")
    public ResponseEntity<Long> getUnreadCount(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        log.info("Received request to get unread count for user {}", userId);
        long count = notificationQueryService.getUnreadCount(userId);
        log.info("User {} has {} unread notifications", userId, count);
        return ResponseEntity.ok(count);
    }
}
