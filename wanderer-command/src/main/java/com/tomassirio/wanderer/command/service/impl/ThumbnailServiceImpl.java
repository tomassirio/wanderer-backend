package com.tomassirio.wanderer.command.service.impl;

import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.config.properties.GoogleMapsProperties;
import com.tomassirio.wanderer.command.config.properties.ThumbnailProperties;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
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
    public void generateAndSaveThumbnail(Trip trip) {
        if (!isThumbnailGenerationAvailable()) {
            return;
        }

        if (trip.getTripUpdates() == null || trip.getTripUpdates().isEmpty()) {
            log.debug("Trip {} has no updates, skipping thumbnail generation", trip.getId());
            return;
        }

        Optional<TripLocations> locations = extractTripLocations(trip);
        if (locations.isEmpty()) {
            return;
        }

        TripLocations loc = locations.get();
        String polyline = trip.getEncodedPolyline() != null ? trip.getEncodedPolyline() : "";

        generateAndSave(trip.getId(), ThumbnailEntityType.TRIP, loc.start(), loc.end(), polyline);
    }

    @Override
    public void generateAndSaveThumbnail(TripPlan tripPlan) {
        if (!isThumbnailGenerationAvailable()) {
            return;
        }

        if (tripPlan.getPlannedPolyline() == null || tripPlan.getPlannedPolyline().isEmpty()) {
            log.debug(
                    "TripPlan {} has no planned route, skipping thumbnail generation",
                    tripPlan.getId());
            return;
        }

        generateAndSave(
                tripPlan.getId(),
                ThumbnailEntityType.TRIP_PLAN,
                tripPlan.getStartLocation(),
                tripPlan.getEndLocation(),
                tripPlan.getPlannedPolyline());
    }

    @Override
    public void processAndSaveProfilePicture(
            UUID userId, byte[] fileBytes, String contentType, String originalFilename) {
        log.debug("Processing profile picture upload for user: {}", userId);

        try {
            // Validate file
            validateProfilePicture(contentType, originalFilename, fileBytes.length);

            ensureStorageDirectoryExists(ThumbnailEntityType.USER_PROFILE);

            // Resize image
            byte[] resizedBytes = resizeImage(fileBytes, 512, 512);

            String filename = userId + PNG_EXTENSION;
            Path filePath = getStoragePath(ThumbnailEntityType.USER_PROFILE).resolve(filename);
            Files.write(filePath, resizedBytes);

            log.info(
                    "Successfully saved profile picture for user {} to {}",
                    userId,
                    filePath.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to process profile picture for user {}", userId, e);
            throw new IllegalArgumentException(
                    "Failed to process profile picture: " + e.getMessage(), e);
        }
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
                    "Failed to delete thumbnail for {} {}", entityType.name().toLowerCase(), id, e);
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
    private void generateAndSave(
            UUID id,
            ThumbnailEntityType entityType,
            GeoLocation start,
            GeoLocation end,
            String polyline) {
        try {
            ensureStorageDirectoryExists(entityType);

            // Delete existing thumbnail first to ensure cache invalidation
            Path filePath = resolveFilePath(id, entityType);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.debug("Deleted existing thumbnail for {} {}", entityType.name().toLowerCase(), id);
            }

            String staticMapUrl = buildStaticMapUrl(start, end, polyline);
            log.debug("Generating thumbnail from URL: {}", staticMapUrl);

            byte[] imageBytes = downloadImage(staticMapUrl);

            Files.write(filePath, imageBytes);

            log.info(
                    "Successfully saved thumbnail for {} {} to {}",
                    entityType.name().toLowerCase(),
                    id,
                    filePath.toAbsolutePath());

        } catch (IOException e) {
            log.error(
                    "Failed to generate thumbnail for {} {}",
                    entityType.name().toLowerCase(),
                    id,
                    e);
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

        // Google Maps Static API has URL length limit (~8192 chars)
        if (polyline != null && !polyline.isEmpty()) {
            String polylinePath = "&path=color:0x0088ffff|weight:4|enc:" + polyline;
            String keyParam = "&key=" + googleMapsProperties.getApiKey();

            // Estimate final URL length (current + polyline + key)
            int estimatedLength = urlBuilder.length() + polylinePath.length() + keyParam.length();

            if (estimatedLength < 8000) {
                // Polyline fits, use it as-is
                urlBuilder.append(polylinePath);
            } else {
                // Polyline too long - simplify it by taking every Nth point
                log.warn(
                        "Polyline too long ({} chars), simplifying for thumbnail", estimatedLength);
                String simplifiedPolyline = simplifyPolyline(polyline, estimatedLength);
                urlBuilder
                        .append("&path=color:0x0088ffff|weight:4|enc:")
                        .append(simplifiedPolyline);
            }
        }

        urlBuilder.append("&key=").append(googleMapsProperties.getApiKey());

        return urlBuilder.toString();
    }

    /**
     * Simplifies an encoded polyline by decoding, decimating points, and re-encoding. Reduces the
     * number of points to fit within Google Maps Static API URL limits.
     */
    private String simplifyPolyline(String polyline, int currentLength) {
        try {
            // Decode the polyline to lat/lng points
            List<LatLng> points = PolylineEncoding.decode(polyline);

            if (points.isEmpty()) {
                return polyline;
            }

            // Calculate target number of points to fit under URL limit
            // Each point adds ~5-10 chars to encoded string
            int maxPolylineLength = 7000; // Conservative limit
            int targetPoints = Math.min(points.size(), maxPolylineLength / 8);

            // Decimate points - take every Nth point
            int step = Math.max(1, points.size() / targetPoints);
            List<LatLng> simplified = new ArrayList<>();

            // Always include first point
            simplified.add(points.get(0));

            // Take every Nth point
            for (int i = step; i < points.size() - 1; i += step) {
                simplified.add(points.get(i));
            }

            // Always include last point
            if (points.size() > 1) {
                simplified.add(points.get(points.size() - 1));
            }

            // Re-encode the simplified path
            String result = PolylineEncoding.encode(simplified);

            log.info(
                    "Simplified polyline: {} points → {} points, {} chars → {} chars",
                    points.size(),
                    simplified.size(),
                    polyline.length(),
                    result.length());

            return result;

        } catch (Exception e) {
            log.error("Failed to simplify polyline, using markers only", e);
            return ""; // Return empty to fallback to markers only
        }
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

    @SuppressWarnings("deprecation")
    private byte[] downloadImage(String urlString) throws IOException {
        // Using URL class instead of HttpClient because Google Maps URLs contain
        // special characters (|, :) that are valid in URLs but not in URIs
        try (InputStream in = new URL(urlString).openStream()) {
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

    private void validateProfilePicture(
            String contentType, String originalFilename, long fileSize) {
        // Check file size (5MB max)
        long maxSize = 5 * 1024 * 1024;
        if (fileSize > maxSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 5MB");
        }

        log.debug(
                "Validating profile picture: contentType={}, filename={}, size={}",
                contentType,
                originalFilename,
                fileSize);

        // Accept if content type matches OR if filename extension matches
        boolean isValidContentType =
                contentType != null
                        && (contentType.startsWith("image/jpeg")
                                || contentType.startsWith("image/jpg")
                                || contentType.startsWith("image/png")
                                || contentType.startsWith("image/webp"));

        boolean isValidFilename =
                originalFilename != null
                        && (originalFilename.toLowerCase().endsWith(".jpg")
                                || originalFilename.toLowerCase().endsWith(".jpeg")
                                || originalFilename.toLowerCase().endsWith(".png")
                                || originalFilename.toLowerCase().endsWith(".webp"));

        if (!isValidContentType && !isValidFilename) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid file type. Only JPEG, PNG, and WebP are allowed. Received contentType=%s, filename=%s",
                            contentType, originalFilename));
        }
    }

    private byte[] resizeImage(byte[] imageBytes, int targetWidth, int targetHeight)
            throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage originalImage = ImageIO.read(bais);

        if (originalImage == null) {
            throw new IOException("Failed to read image");
        }

        // Create resized image
        BufferedImage resizedImage =
                new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();

        // Use high quality rendering
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();

        // Write to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", baos);
        return baos.toByteArray();
    }

    /** Pair of start and end locations extracted from trip updates. */
    private record TripLocations(GeoLocation start, GeoLocation end) {}
}
