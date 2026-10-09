package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
 * <p>Live tracks publish a polyline update every couple of minutes, so while a trip is not FINISHED
 * the (paid Static Maps) thumbnail is regenerated at most once per {@link
 * #THUMBNAIL_REFRESH_INTERVAL}. The throttle reads the thumbnail file's mtime: the storage volume
 * is already the shared source of truth for thumbnails, so it holds across instances and restarts
 * with no extra state. Explicit rebuilds ({@link PolylineUpdatedEvent#isForceThumbnail()}) and the
 * trip becoming FINISHED always regenerate, so the final route is drawn even if the last batch was
 * throttled.
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

    static final Duration THUMBNAIL_REFRESH_INTERVAL = Duration.ofMinutes(15);

    private final TripRepository tripRepository;
    private final ThumbnailService thumbnailService;

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PolylineUpdatedEvent event) {
        generate(event.getTripId(), event.isForceThumbnail());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTripFinished(TripStatusChangedEvent event) {
        if (TripStatus.FINISHED.name().equals(event.getNewStatus())) {
            generate(event.getTripId(), true);
        }
    }

    private void generate(UUID tripId, boolean force) {
        log.debug("Generating thumbnail for trip: {}", tripId);

        try {
            Trip trip =
                    tripRepository
                            .findById(tripId)
                            .orElseThrow(
                                    () -> new IllegalStateException("Trip not found: " + tripId));

            if (!force && !isFinished(trip) && refreshedRecently(tripId)) {
                log.debug("Thumbnail for trip {} refreshed recently, skipping", tripId);
                return;
            }

            thumbnailService.generateAndSaveThumbnail(trip);
            log.info("Successfully generated and saved thumbnail for trip {}", tripId);

        } catch (Exception e) {
            log.error("Failed to generate or save thumbnail for trip {}", tripId, e);
        }
    }

    private static boolean isFinished(Trip trip) {
        return trip.getTripSettings() != null
                && trip.getTripSettings().getTripStatus() == TripStatus.FINISHED;
    }

    private boolean refreshedRecently(UUID tripId) {
        Instant cutoff = Instant.now().minus(THUMBNAIL_REFRESH_INTERVAL);
        return thumbnailService
                .thumbnailLastModified(tripId, ThumbnailEntityType.TRIP)
                .filter(modified -> modified.isAfter(cutoff))
                .isPresent();
    }
}
