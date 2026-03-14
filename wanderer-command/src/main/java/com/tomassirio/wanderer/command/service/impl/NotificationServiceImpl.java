package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.repository.NotificationRepository;
import com.tomassirio.wanderer.command.service.NotificationService;
import com.tomassirio.wanderer.commons.domain.Notification;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public void markAsRead(UUID userId, UUID notificationId) {
        log.info("Marking notification {} as read for user {}", notificationId, userId);

        Notification notification =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "Notification not found: " + notificationId));

        if (!notification.getRecipientId().equals(userId)) {
            throw new AccessDeniedException(
                    "User " + userId + " is not the recipient of notification " + notificationId);
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        log.info("Notification {} marked as read", notificationId);
    }

    @Override
    public int markAllAsRead(UUID userId) {
        log.info("Marking all notifications as read for user {}", userId);
        int count = notificationRepository.markAllAsReadByRecipientId(userId);
        log.info("Marked {} notifications as read for user {}", count, userId);
        return count;
    }
}

