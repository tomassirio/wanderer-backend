package com.tomassirio.wanderer.command.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.tomassirio.wanderer.command.repository.NotificationRepository;
import com.tomassirio.wanderer.commons.domain.Notification;
import com.tomassirio.wanderer.commons.domain.NotificationType;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private NotificationServiceImpl notificationService;

    private UUID userId;
    private UUID notificationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        notificationId = UUID.randomUUID();

        notification =
                Notification.builder()
                        .id(notificationId)
                        .recipientId(userId)
                        .actorId(otherUserId)
                        .type(NotificationType.FRIEND_REQUEST_RECEIVED)
                        .referenceId(UUID.randomUUID())
                        .message("Test notification")
                        .read(false)
                        .createdAt(Instant.now())
                        .build();
    }

    @Test
    void markAsRead_Success() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(userId, notificationId);

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_NotFound_ThrowsException() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(notificationId.toString());
    }

    @Test
    void markAsRead_NotRecipient_ThrowsAccessDenied() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        UUID wrongUserId = UUID.randomUUID();

        assertThatThrownBy(() -> notificationService.markAsRead(wrongUserId, notificationId))
                .isInstanceOf(AccessDeniedException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllAsRead_Success() {
        when(notificationRepository.markAllAsReadByRecipientId(userId)).thenReturn(5);

        int count = notificationService.markAllAsRead(userId);

        assertThat(count).isEqualTo(5);
        verify(notificationRepository).markAllAsReadByRecipientId(userId);
    }

    @Test
    void markAllAsRead_NoUnread_ReturnsZero() {
        when(notificationRepository.markAllAsReadByRecipientId(userId)).thenReturn(0);

        int count = notificationService.markAllAsRead(userId);

        assertThat(count).isZero();
    }
}

