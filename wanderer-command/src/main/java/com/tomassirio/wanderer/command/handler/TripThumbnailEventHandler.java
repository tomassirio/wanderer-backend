package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.Trip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for generating trip thumbnails when trip updates are created.
 *
 * <p>This handler listens to {@link TripUpdatedEvent} and generates a map thumbnail for the trip
 * using Google Maps Static API. The thumbnail is saved to persistent storage and the URL is stored
 * in the trip entity.
 *
 * <p>Thumbnail generation is performed asynchronously to avoid blocking the main transaction.
 *
 * @author tomassirio
 * @since 0.10.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripThumbnailEventHandler implements EventHandler<TripUpdatedEvent> {

    private final TripRepository tripRepository;
    private final ThumbnailService thumbnailService;

    @Override
    @EventListener
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(TripUpdatedEvent event) {
        log.debug("Generating thumbnail for trip: {}", event.getTripId());

        try {
            Trip trip =
                    tripRepository
                            .findById(event.getTripId())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Trip not found: " + event.getTripId()));

            String thumbnailUrl = thumbnailService.generateAndSaveThumbnail(trip);
            log.debug("Generated thumbnail URL for trip {}: {}", event.getTripId(), thumbnailUrl);

            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                trip.setThumbnailUrl(thumbnailUrl);
                log.debug("Set thumbnailUrl on trip entity: {}", thumbnailUrl);

                tripRepository.saveAndFlush(trip);
                log.info(
                        "Successfully updated trip {} with thumbnail URL: {}",
                        event.getTripId(),
                        thumbnailUrl);
            } else {
                log.warn("Thumbnail generation returned null/empty for trip {}", event.getTripId());
            }

        } catch (Exception e) {
            log.error("Failed to generate or save thumbnail for trip {}", event.getTripId(), e);
            throw e; // Re-throw to ensure transaction rollback is visible
        }
    }
}
