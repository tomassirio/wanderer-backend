package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for generating and managing trip and trip plan map thumbnails.
 *
 * <p>This service handles thumbnail generation for trips and trip plans using Google Maps Static
 * API, saving images to persistent storage, and managing their lifecycle.
 *
 * @author tomassirio
 * @since 0.10.5
 */
public interface ThumbnailService {

    /**
     * Generates a map thumbnail for a trip and saves it to persistent storage.
     *
     * <p>The thumbnail is generated using Google Maps Static API with the trip's polyline and
     * start/end markers. The image is downloaded and saved to the configured storage path.
     *
     * @param trip the trip to generate a thumbnail for
     */
    void generateAndSaveThumbnail(Trip trip);

    /**
     * Generates a map thumbnail for a trip plan and saves it to persistent storage.
     *
     * <p>The thumbnail is generated using Google Maps Static API with the trip plan's planned route
     * polyline and start/end markers. The image is downloaded and saved to the configured storage
     * path.
     *
     * @param tripPlan the trip plan to generate a thumbnail for
     */
    void generateAndSaveThumbnail(TripPlan tripPlan);

    /**
     * Processes and saves a profile picture from byte array.
     *
     * <p>Validates the file type (JPEG, PNG, WebP), size (max 5MB), and dimensions. Resizes the
     * image to 512x512 and saves it to persistent storage.
     *
     * @param userId the UUID of the user
     * @param fileBytes the image file bytes
     * @param contentType the content type of the image
     * @param originalFilename the original filename
     * @throws IllegalArgumentException if file type is invalid, size exceeds limit, or processing
     *     fails
     */
    void processAndSaveProfilePicture(UUID userId, byte[] fileBytes, String contentType, String originalFilename);

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
