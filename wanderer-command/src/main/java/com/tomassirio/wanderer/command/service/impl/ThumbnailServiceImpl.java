package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.config.properties.GoogleMapsProperties;
import com.tomassirio.wanderer.command.config.properties.ThumbnailProperties;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Implementation of ThumbnailService that generates Google Maps thumbnails and stores them in
 * persistent volume.
 *
 * @author tomassirio
 * @since 0.10.5
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties({ThumbnailProperties.class, GoogleMapsProperties.class})
public class ThumbnailServiceImpl implements ThumbnailService {

    private static final String PNG_EXTENSION = ".png";

    private final ThumbnailProperties thumbnailProperties;
    private final GoogleMapsProperties googleMapsProperties;

    @Override
    public String generateAndSaveThumbnail(Trip trip) {
        if (!isThumbnailGenerationAvailable()) {
            return null;
        }

        if (trip.getTripUpdates() == null || trip.getTripUpdates().isEmpty()) {
            log.debug("Trip {} has no updates, skipping thumbnail generation", trip.getId());
            return null;
        }

        Optional<TripLocations> locations = extractTripLocations(trip);
        if (locations.isEmpty()) {
            return null;
        }

        TripLocations loc = locations.get();
        String polyline = trip.getEncodedPolyline() != null ? trip.getEncodedPolyline() : "";

        return generateSaveAndReturnUrl(
                trip.getId(), ThumbnailEntityType.TRIP, loc.start(), loc.end(), polyline);
    }

    @Override
    public String generateAndSaveThumbnail(TripPlan tripPlan) {
        if (!isThumbnailGenerationAvailable()) {
            return null;
        }

        if (tripPlan.getPlannedPolyline() == null || tripPlan.getPlannedPolyline().isEmpty()) {
            log.debug(
                    "TripPlan {} has no planned route, skipping thumbnail generation",
                    tripPlan.getId());
            return null;
        }

        return generateSaveAndReturnUrl(
                tripPlan.getId(),
                ThumbnailEntityType.TRIP_PLAN,
                tripPlan.getStartLocation(),
                tripPlan.getEndLocation(),
                tripPlan.getPlannedPolyline());
    }

    @Override
    public void deleteThumbnail(UUID id, ThumbnailEntityType entityType) {
        try {
            Path filePath = resolveFilePath(id, entityType);

            if (Files.deleteIfExists(filePath)) {
                log.info("Deleted thumbnail for {} {}", entityType.name().toLowerCase(), id);
            } else {
                log.debug("No thumbnail file found for {} {}", entityType.name().toLowerCase(), id);
            }
        } catch (IOException e) {
            log.error(
                    "Failed to delete thumbnail for {} {}",
                    entityType.name().toLowerCase(),
                    id,
                    e);
        }
    }

    @Override
    public boolean thumbnailExists(UUID id, ThumbnailEntityType entityType) {
        return Files.exists(resolveFilePath(id, entityType));
    }

    /**
     * Checks whether thumbnail generation is available based on configuration.
     *
     * @return true if thumbnail generation is enabled and Google Maps API is properly configured
     */
    private boolean isThumbnailGenerationAvailable() {
        if (!thumbnailProperties.isEnabled()) {
            log.debug("Thumbnail generation is disabled");
            return false;
        }

        if (!googleMapsProperties.isEnabled()
                || googleMapsProperties.getApiKey() == null
                || googleMapsProperties.getApiKey().isEmpty()) {
            log.warn("Google Maps API is not configured, cannot generate thumbnail");
            return false;
        }

        return true;
    }

    /**
     * Core pipeline: builds a static map URL, downloads the image, saves it to disk, and returns
     * the public URL.
     */
    private String generateSaveAndReturnUrl(
            UUID id,
            ThumbnailEntityType entityType,
            GeoLocation start,
            GeoLocation end,
            String polyline) {
        try {
            ensureStorageDirectoryExists(entityType);

            String staticMapUrl = buildStaticMapUrl(start, end, polyline);
            log.debug("Generating thumbnail for {} {}", entityType.name().toLowerCase(), id);

            byte[] imageBytes = downloadImage(staticMapUrl);

            String filename = id + PNG_EXTENSION;
            Path filePath = getStoragePath(entityType).resolve(filename);
            Files.write(filePath, imageBytes);

            log.info(
                    "Successfully saved thumbnail for {} {} to {}",
                    entityType.name().toLowerCase(),
                    id,
                    filePath.toAbsolutePath());

            return thumbnailProperties.getBaseUrl()
                    + "/"
                    + entityType.getSubdirectory()
                    + "/"
                    + filename;

        } catch (IOException e) {
            log.error(
                    "Failed to generate thumbnail for {} {}",
                    entityType.name().toLowerCase(),
                    id,
                    e);
            return null;
        }
    }

    /**
     * Extracts the first and last valid locations from a trip's updates.
     *
     * @return an optional containing the start and end locations, or empty if no valid updates
     *     exist
     */
    private Optional<TripLocations> extractTripLocations(Trip trip) {
        List<TripUpdate> updates =
                trip.getTripUpdates().stream()
                        .filter(u -> u.getLocation() != null)
                        .sorted(Comparator.comparing(TripUpdate::getTimestamp))
                        .toList();

        if (updates.isEmpty()) {
            log.warn("Trip {} has no valid location updates", trip.getId());
            return Optional.empty();
        }

        GeoLocation start = updates.getFirst().getLocation();
        GeoLocation end = updates.getLast().getLocation();
        return Optional.of(new TripLocations(start, end));
    }

    private String buildStaticMapUrl(GeoLocation start, GeoLocation end, String polyline) {
        StringBuilder urlBuilder =
                new StringBuilder("https://maps.googleapis.com/maps/api/staticmap?");
        urlBuilder
                .append("size=")
                .append(thumbnailProperties.getWidth())
                .append("x")
                .append(thumbnailProperties.getHeight());

        appendMarker(urlBuilder, "green", "A", start);
        appendMarker(urlBuilder, "red", "B", end);

        if (polyline != null && !polyline.isEmpty()) {
            urlBuilder.append("&path=color:0x0088ffff|weight:4|enc:").append(polyline);
        }

        urlBuilder.append("&key=").append(googleMapsProperties.getApiKey());

        return urlBuilder.toString();
    }

    private void appendMarker(
            StringBuilder urlBuilder, String color, String label, GeoLocation location) {
        urlBuilder
                .append("&markers=color:")
                .append(color)
                .append("|label:")
                .append(label)
                .append("|")
                .append(location.getLat())
                .append(",")
                .append(location.getLon());
    }

    private byte[] downloadImage(String urlString) throws IOException {
        try (InputStream in = URI.create(urlString).toURL().openStream()) {
            return in.readAllBytes();
        }
    }

    private Path resolveFilePath(UUID id, ThumbnailEntityType entityType) {
        return getStoragePath(entityType).resolve(id + PNG_EXTENSION);
    }

    private Path getStoragePath(ThumbnailEntityType entityType) {
        return Paths.get(thumbnailProperties.getStoragePath(), entityType.getSubdirectory());
    }

    private void ensureStorageDirectoryExists(ThumbnailEntityType entityType) throws IOException {
        Path storagePath = getStoragePath(entityType);
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
            log.info("Created thumbnail storage directory: {}", storagePath.toAbsolutePath());
        }
    }

    /** Pair of start and end locations extracted from trip updates. */
    private record TripLocations(GeoLocation start, GeoLocation end) {}
}
