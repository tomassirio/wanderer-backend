package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripPlanCreatedEvent;
import com.tomassirio.wanderer.command.event.TripPlanUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Event handler for generating thumbnails for trip plans asynchronously.
 *
 * <p>Listens to TripPlanCreatedEvent and TripPlanUpdatedEvent to generate map thumbnails showing
 * the planned route.
 *
 * @author tomassirio
 * @since 0.10.5
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TripPlanThumbnailEventHandler {

    private final ThumbnailService thumbnailService;
    private final TripPlanRepository tripPlanRepository;

    @Async
    @EventListener
    @Transactional
    public void handle(TripPlanCreatedEvent event) {
        log.debug("Generating thumbnail for newly created trip plan: {}", event.getTripPlanId());
        generateAndSaveThumbnail(event.getTripPlanId());
    }

    @Async
    @EventListener
    @Transactional
    public void handle(TripPlanUpdatedEvent event) {
        log.debug("Regenerating thumbnail for updated trip plan: {}", event.getTripPlanId());
        generateAndSaveThumbnail(event.getTripPlanId());
    }

    private void generateAndSaveThumbnail(UUID tripPlanId) {
        TripPlan tripPlan =
                tripPlanRepository
                        .findById(tripPlanId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Trip plan not found: " + tripPlanId));

        String thumbnailUrl = thumbnailService.generateAndSaveThumbnail(tripPlan);

        if (thumbnailUrl != null) {
            log.debug("Generated thumbnail URL for trip plan {}: {}", tripPlanId, thumbnailUrl);
            log.debug("Set thumbnailUrl on trip plan entity: {}", thumbnailUrl);

            tripPlan.setThumbnailUrl(thumbnailUrl);
            tripPlanRepository.saveAndFlush(tripPlan);

            log.info(
                    "Successfully updated trip plan {} with thumbnail URL: {}",
                    tripPlanId,
                    thumbnailUrl);
        } else {
            log.warn("Failed to generate thumbnail for trip plan {}", tripPlanId);
        }
    }
}
