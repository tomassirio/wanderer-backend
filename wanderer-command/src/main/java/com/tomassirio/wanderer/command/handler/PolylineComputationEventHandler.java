package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TrackPointsRecordedEvent;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.service.PolylineService;
import com.tomassirio.wanderer.command.service.TrackPointService;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asynchronous event handler that computes/updates the encoded polyline for a trip after a trip
 * update is persisted.
 *
 * <p>This handler runs <strong>after the main transaction commits</strong> and on a separate
 * thread, so it never blocks the user-facing response. When a new trip update is added, it
 * incrementally appends the new route segment to the existing polyline; when track points arrive,
 * the route is rebuilt from the recorded track instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolylineComputationEventHandler {

    private final PolylineService polylineService;
    private final TrackPointService trackPointService;

    /**
     * Handles a TripUpdatedEvent by incrementally appending the new segment to the trip's polyline.
     *
     * <p>Runs asynchronously after the transaction that persisted the trip update commits.
     *
     * @param event the trip updated event
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTripUpdated(TripUpdatedEvent event) {
        log.debug("Async polyline computation triggered for trip: {}", event.getTripId());
        try {
            polylineService.appendSegment(event.getTripId());
        } catch (Exception e) {
            log.error(
                    "Failed to compute polyline for trip {}: {}",
                    event.getTripId(),
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Recomputes the trip's route and distance from its track points after a batch of points is
     * committed, and broadcasts the newly accepted points.
     *
     * @param event the track points recorded event
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTrackPointsRecorded(TrackPointsRecordedEvent event) {
        try {
            trackPointService.recomputeTrack(
                    event.getTripId(),
                    event.getPoints().stream()
                            .map(p -> new TrackPointDTO(p.getLat(), p.getLon(), p.getRecordedAt()))
                            .toList());
        } catch (Exception e) {
            log.error(
                    "Failed to recompute track for trip {}: {}",
                    event.getTripId(),
                    e.getMessage(),
                    e);
        }
    }
}
