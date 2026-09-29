package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.Trip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event handler for generating trip thumbnails whenever a trip's polyline is (re)computed.
 *
 * <p>The thumbnail draws the trip's encoded polyline, so it listens to {@link PolylineUpdatedEvent}
 * rather than to trip updates: every new trip update triggers a polyline computation which then
 * publishes this event (including when the polyline is cleared for trips with fewer than two
 * locations), and the admin "recompute polyline" action publishes it too. Generating here means
 * exactly one thumbnail per change, always drawn with the freshest polyline.
 *
 * <p>The event is published inside the polyline service transaction; this handler runs
 * asynchronously after that transaction commits.
 *
 * <p>If the polyline computation fails, no event is published and that update gets no thumbnail
 * refresh; {@code POST /api/1/admin/trips/thumbnails/regenerate-missing} recovers missing ones.
 *
 * @author tomassirio
 * @since 0.10.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripThumbnailEventHandler implements EventHandler<PolylineUpdatedEvent> {

    private final TripRepository tripRepository;
    private final ThumbnailService thumbnailService;

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PolylineUpdatedEvent event) {
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
