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
 *   <li>When status becomes IN_PROGRESS: adds or updates the active trip record
 *   <li>When status changes away from IN_PROGRESS: removes the active trip record
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
     * @param userId the ID of the user who owns the trip
     * @param tripId the ID of the trip
     * @param newStatus the new status of the trip
     */
    public void manageActiveTrip(UUID userId, UUID tripId, TripStatus newStatus) {
        if (newStatus == TripStatus.IN_PROGRESS) {
            // Add or update active trip record
            ActiveTrip activeTrip =
                    activeTripRepository
                            .findById(userId)
                            .orElse(ActiveTrip.builder().userId(userId).build());
            activeTrip.setTripId(tripId);
            activeTripRepository.save(activeTrip);
            log.debug("Set active trip for user {}: {}", userId, tripId);
        } else {
            // Remove active trip record if exists
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
}

