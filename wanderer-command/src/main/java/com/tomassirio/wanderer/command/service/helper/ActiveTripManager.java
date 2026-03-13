package com.tomassirio.wanderer.command.service.helper;

import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.commons.domain.ActiveTrip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper component responsible for managing the active_trips table based on trip status changes.
 *
 * <p>Enforces the business rule that a user can have only one trip in progress at a time.
 *
 * <ul>
 *   <li>When status becomes IN_PROGRESS, PAUSED, or RESTING: adds or updates the active trip record
 *       (these are all "ongoing" states where the trip has not ended)
 *   <li>When status becomes FINISHED or CREATED: removes the active trip record
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveTripManager {

    private final ActiveTripRepository activeTripRepository;

    /**
     * Manages the active_trips table based on trip status changes.
     *
     * <p>PAUSED and RESTING are still considered active states — the trip has not ended, so the
     * active trip record must be preserved to prevent the user from starting a second trip.
     *
     * @param userId the ID of the user who owns the trip
     * @param tripId the ID of the trip
     * @param newStatus the new status of the trip
     */
    public void manageActiveTrip(UUID userId, UUID tripId, TripStatus newStatus) {
        if (isOngoingStatus(newStatus)) {
            // Add or update active trip record for ongoing statuses
            ActiveTrip activeTrip =
                    activeTripRepository
                            .findById(userId)
                            .orElse(ActiveTrip.builder().userId(userId).build());
            activeTrip.setTripId(tripId);
            activeTripRepository.save(activeTrip);
            log.debug("Set active trip for user {}: {}", userId, tripId);
        } else {
            // Remove active trip record for terminal/initial statuses (FINISHED, CREATED)
            activeTripRepository
                    .findById(userId)
                    .filter(activeTrip -> activeTrip.getTripId().equals(tripId))
                    .ifPresent(
                            activeTrip -> {
                                activeTripRepository.delete(activeTrip);
                                log.debug("Removed active trip for user {}", userId);
                            });
        }
    }

    /**
     * Returns whether the given status represents an ongoing trip that should keep the active trip
     * record. IN_PROGRESS, PAUSED, and RESTING are all ongoing — only FINISHED and CREATED are not.
     */
    private boolean isOngoingStatus(TripStatus status) {
        return status == TripStatus.IN_PROGRESS
                || status == TripStatus.PAUSED
                || status == TripStatus.RESTING;
    }
}
