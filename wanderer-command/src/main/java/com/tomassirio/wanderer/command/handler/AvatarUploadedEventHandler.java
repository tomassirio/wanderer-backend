package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.AvatarUploadedEvent;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarUploadedEventHandler implements EventHandler<AvatarUploadedEvent> {

    private final ThumbnailService thumbnailService;

    @Override
    @EventListener
    @Async
    @Transactional
    public void handle(AvatarUploadedEvent event) {
        log.debug("Processing avatar upload for user: {}", event.getUserId());

        try {
            thumbnailService.processAndSaveProfilePicture(event.getUserId(), event.getFile());
            log.info("Successfully processed avatar for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process avatar for user: {}", event.getUserId(), e);
        }
    }
}
