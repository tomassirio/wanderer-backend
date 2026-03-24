package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.AvatarDeletedEvent;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
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
public class AvatarDeletedEventHandler implements EventHandler<AvatarDeletedEvent> {

    private final ThumbnailService thumbnailService;

    @Override
    @EventListener
    @Async
    @Transactional
    public void handle(AvatarDeletedEvent event) {
        log.debug("Processing avatar deletion for user: {}", event.getUserId());

        try {
            thumbnailService.deleteThumbnail(event.getUserId(), ThumbnailEntityType.USER_PROFILE);
            log.info("Successfully deleted avatar for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to delete avatar for user: {}", event.getUserId(), e);
        }
    }
}
