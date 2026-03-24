package com.tomassirio.wanderer.command.service.helper;

import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper component responsible for creating system-generated trip updates for significant lifecycle
 * transitions.
 *
 * <ul>
 *   <li>CREATED → IN_PROGRESS: {@link UpdateType#TRIP_STARTED}
 *   <li>IN_PROGRESS → RESTING: {@link UpdateType#DAY_END}
 *   <li>RESTING → IN_PROGRESS: {@link UpdateType#DAY_START}
 *   <li>* → FINISHED: {@link UpdateType#TRIP_ENDED}
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LifecycleTripUpdateManager {

    private final TripUpdateRepository tripUpdateRepository;

    /**
     * Creates a system-generated trip update if the given status transition warrants one.
     *
     * @param trip the trip to create the update for
     * @param previousStatus the previous trip status
     * @param newStatus the new trip status
     */
    public void createLifecycleTripUpdate(
            Trip trip, TripStatus previousStatus, TripStatus newStatus) {
        UpdateType updateType = resolveLifecycleUpdateType(previousStatus, newStatus);
        if (updateType == null) {
            return;
        }

        // Get current accrued distance for this lifecycle marker
        Double distanceSoFar = trip.getCachedDistanceKm();

        TripUpdate tripUpdate =
                TripUpdate.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .updateType(updateType)
                        .distanceSoFarKm(distanceSoFar)
                        .timestamp(Instant.now())
                        .build();

        tripUpdateRepository.save(tripUpdate);
        log.info(
                "Created lifecycle trip update {} for trip {} with distance {} km",
                updateType,
                trip.getId(),
                distanceSoFar);
    }

    /**
     * Determines the {@link UpdateType} for a lifecycle transition, or {@code null} if the
     * transition does not warrant a system-generated update.
     *
     * @param previousStatus the previous trip status
     * @param newStatus the new trip status
     * @return the update type, or {@code null} if no update is needed
     */
    private UpdateType resolveLifecycleUpdateType(TripStatus previousStatus, TripStatus newStatus) {
        if (newStatus == TripStatus.IN_PROGRESS && previousStatus == TripStatus.CREATED) {
            return UpdateType.TRIP_STARTED;
        }
        if (newStatus == TripStatus.FINISHED) {
            return UpdateType.TRIP_ENDED;
        }
        if (newStatus == TripStatus.RESTING && previousStatus == TripStatus.IN_PROGRESS) {
            return UpdateType.DAY_END;
        }
        if (newStatus == TripStatus.IN_PROGRESS && previousStatus == TripStatus.RESTING) {
            return UpdateType.DAY_START;
        }
        return null;
    }
}
