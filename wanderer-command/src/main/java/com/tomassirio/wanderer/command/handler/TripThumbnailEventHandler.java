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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event handler for generating trip thumbnails when trip updates are created.
 *
 * <p>This handler listens to {@link TripUpdatedEvent} and generates a map thumbnail for the trip
 * using Google Maps Static API. The thumbnail is saved to persistent storage.
 *
 * <p>Thumbnail generation is performed asynchronously to avoid blocking the main transaction,
 * and uses {@link TransactionalEventListener} to ensure the trip data is fully committed before
 * generating the thumbnail (so all trip updates are available).
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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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

            thumbnailService.generateAndSaveThumbnail(trip);
            log.info("Successfully generated and saved thumbnail for trip {}", event.getTripId());

        } catch (Exception e) {
            log.error("Failed to generate or save thumbnail for trip {}", event.getTripId(), e);
        }
    }
}
