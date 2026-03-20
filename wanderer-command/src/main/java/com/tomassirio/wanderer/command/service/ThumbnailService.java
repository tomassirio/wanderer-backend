package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.commons.domain.Trip;
import java.util.UUID;

/**
 * Service interface for generating and managing trip map thumbnails.
 *
 * <p>This service handles thumbnail generation for trips using Google Maps Static API, saving
 * images to persistent storage, and managing their lifecycle.
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
     * Deletes the thumbnail file for a given trip.
     *
     * @param tripId the UUID of the trip whose thumbnail should be deleted
     */
    void deleteThumbnail(UUID tripId);

    /**
     * Checks if a thumbnail exists for a given trip.
     *
     * @param tripId the UUID of the trip
     * @return true if a thumbnail file exists, false otherwise
     */
    boolean thumbnailExists(UUID tripId);
}
