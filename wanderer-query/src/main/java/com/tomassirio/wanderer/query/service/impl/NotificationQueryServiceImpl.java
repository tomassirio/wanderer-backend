package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.dto.NotificationDTO;
import com.tomassirio.wanderer.commons.mapper.NotificationMapper;
import com.tomassirio.wanderer.query.repository.NotificationRepository;
import com.tomassirio.wanderer.query.service.NotificationQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper = NotificationMapper.INSTANCE;

    @Override
    public Page<NotificationDTO> getNotifications(UUID recipientId, Pageable pageable) {
        log.info("Fetching notifications for user {}", recipientId);
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)
                .map(notificationMapper::toDTO);
    }

    @Override
    public long getUnreadCount(UUID recipientId) {
        log.info("Fetching unread notification count for user {}", recipientId);
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }
}

