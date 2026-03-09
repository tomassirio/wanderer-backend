package com.tomassirio.wanderer.command.service;

import java.util.UUID;

/**
 * Service interface for managing trip day operations in the command side of the CQRS architecture.
 *
 * <p>This service handles day toggling for multi-day trips, allowing users to end the current day
 * (transition to RESTING) or start a new day (transition to IN_PROGRESS).
 *
 * @author tomassirio
 * @since 0.9.1
 */
public interface TripDayService {

    /**
     * Toggles the day state of a multi-day trip.
     *
     * <p>If the trip is currently IN_PROGRESS, the day is finished and the trip status changes to
     * RESTING. If the trip is currently RESTING, a new day starts and the trip status changes to
     * IN_PROGRESS.
     *
     * <p>This operation is only valid for trips with MULTI_DAY modality.
     *
     * @param userId the UUID of the user making the request (for ownership validation)
     * @param id the UUID of the trip
     * @return the UUID of the trip
     * @throws jakarta.persistence.EntityNotFoundException if no trip exists with the given ID
     * @throws org.springframework.security.access.AccessDeniedException if user doesn't own the
     *     trip
     * @throws IllegalStateException if the trip is not MULTI_DAY or not in a valid status
     */
    UUID toggleDay(UUID userId, UUID id);
}

