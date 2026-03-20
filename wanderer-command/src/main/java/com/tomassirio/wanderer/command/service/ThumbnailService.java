package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import java.util.UUID;

/**
 * Service interface for generating and managing trip and trip plan map thumbnails.
 *
 * <p>This service handles thumbnail generation for trips and trip plans using Google Maps Static API,
 * saving images to persistent storage, and managing their lifecycle.
 *
 * @author tomassirio
 * @since 0.10.5
 */
public interface ThumbnailService {

    /**
     * Generates a map thumbnail for a trip and saves it to persistent storage.
     *
     * <p>The thumbnail is generated using Google Maps Static API with the trip's polyline and
     * start/end markers. The image is downloaded and saved to the configured storage path, and the
     * public URL is returned.
     *
     * @param trip the trip to generate a thumbnail for
     * @return the public URL of the generated thumbnail, or null if generation failed or trip has
     *     no updates
     */
    String generateAndSaveThumbnail(Trip trip);

    /**
     * Generates a map thumbnail for a trip plan and saves it to persistent storage.
     *
     * <p>The thumbnail is generated using Google Maps Static API with the trip plan's planned route
     * polyline and start/end markers. The image is downloaded and saved to the configured storage path.
     *
     * @param tripPlan the trip plan to generate a thumbnail for
     * @return the public URL of the generated thumbnail, or null if generation failed or trip plan has
     *     no route
     */
    String generateAndSaveThumbnail(TripPlan tripPlan);

    /**
     * Deletes the thumbnail file for a given entity.
     *
     * @param id the UUID of the entity whose thumbnail should be deleted
     * @param entityType the type of entity (TRIP, TRIP_PLAN, USER_PROFILE)
     */
    void deleteThumbnail(UUID id, ThumbnailEntityType entityType);

    /**
     * Checks if a thumbnail exists for a given entity.
     *
     * @param id the UUID of the entity
     * @param entityType the type of entity (TRIP, TRIP_PLAN, USER_PROFILE)
     * @return true if a thumbnail file exists, false otherwise
     */
    boolean thumbnailExists(UUID id, ThumbnailEntityType entityType);
}
