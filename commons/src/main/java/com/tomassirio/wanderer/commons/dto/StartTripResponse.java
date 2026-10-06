package com.tomassirio.wanderer.commons.dto;

import com.tomassirio.wanderer.commons.domain.TripStatus;
import java.util.UUID;

/**
 * Result of {@code POST /trips/start}.
 *
 * @param tripId the started trip
 * @param tripUpdateId the TRIP_STARTED check-in
 * @param status the trip's current status
 * @param replayed {@code true} when the Idempotency-Key was already used and no trip was created
 */
public record StartTripResponse(
        UUID tripId, UUID tripUpdateId, TripStatus status, boolean replayed) {}
