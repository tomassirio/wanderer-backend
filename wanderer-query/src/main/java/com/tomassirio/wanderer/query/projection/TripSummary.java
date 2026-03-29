package com.tomassirio.wanderer.query.projection;

import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection for Trip entity containing only summary information.
 * Used for list views to reduce data transfer and improve performance.
 */
public interface TripSummary {
    UUID getId();
    String getName();
    UUID getUserId();
    TripVisibility getTripSettingsVisibility();
    TripStatus getTripSettingsStatus();
    TripModality getTripSettingsModality();
    Instant getCreationTimestamp();
    Double getCachedDistanceKm();
}
