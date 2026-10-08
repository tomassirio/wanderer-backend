package com.tomassirio.wanderer.command.service;

import java.util.UUID;

/**
 * Service for batch-recomputing reverse-geocoded city/country on trip updates.
 *
 * @since 0.8.3
 */
public interface TripUpdateGeocodingService {

    /**
     * Re-runs reverse geocoding on every trip update belonging to the given trip, updating only the
     * {@code city} and {@code country} fields. All other fields are left untouched.
     *
     * @param tripId the UUID of the trip whose updates should be re-geocoded
     */
    void recomputeGeocoding(UUID tripId);

    /**
     * Fills in city, country and weather on a freshly created check-in and broadcasts {@code
     * TRIP_UPDATE_ENRICHED}. When the previous enriched check-in is within 300 m and 30 minutes,
     * its values are copied instead of calling the external APIs.
     *
     * @param tripUpdateId the UUID of the check-in to enrich
     */
    void enrichTripUpdate(UUID tripUpdateId);
}
