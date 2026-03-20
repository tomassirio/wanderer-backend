package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.config.properties.GoogleMapsProperties;
import com.tomassirio.wanderer.command.config.properties.ThumbnailProperties;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
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

    private final ThumbnailProperties thumbnailProperties;
    private final GoogleMapsProperties googleMapsProperties;

    @Override
    public String generateAndSaveThumbnail(Trip trip) {
        if (!thumbnailProperties.isEnabled()) {
            log.debug("Thumbnail generation is disabled");
            return null;
        }

        if (!googleMapsProperties.isEnabled()
                || googleMapsProperties.getApiKey() == null
                || googleMapsProperties.getApiKey().isEmpty()) {
            log.warn("Google Maps API is not configured, cannot generate thumbnail");
            return null;
        }

        if (trip.getTripUpdates() == null || trip.getTripUpdates().isEmpty()) {
            log.debug("Trip {} has no updates, skipping thumbnail generation", trip.getId());
            return null;
        }

        try {
            ensureStorageDirectoryExists();

            String staticMapUrl = buildStaticMapUrl(trip);
            log.debug("Generating thumbnail from URL: {}", staticMapUrl);

            byte[] imageBytes = downloadImage(staticMapUrl);

            String filename = trip.getId() + ".png";
            Path filePath = getStoragePath().resolve(filename);
            Files.write(filePath, imageBytes);

            log.info(
                    "Successfully saved thumbnail for trip {} to {}",
                    trip.getId(),
                    filePath.toAbsolutePath());

            return thumbnailProperties.getBaseUrl() + "/" + filename;

        } catch (IOException e) {
            log.error("Failed to generate thumbnail for trip {}", trip.getId(), e);
            return null;
        }
    }

    @Override
    public void deleteThumbnail(UUID tripId) {
        try {
            String filename = tripId + ".png";
            Path filePath = getStoragePath().resolve(filename);

            if (Files.deleteIfExists(filePath)) {
                log.info("Deleted thumbnail for trip {}", tripId);
            } else {
                log.debug("No thumbnail file found for trip {}", tripId);
            }
        } catch (IOException e) {
            log.error("Failed to delete thumbnail for trip {}", tripId, e);
        }
    }

    @Override
    public boolean thumbnailExists(UUID tripId) {
        String filename = tripId + ".png";
        Path filePath = getStoragePath().resolve(filename);
        return Files.exists(filePath);
    }

    private String buildStaticMapUrl(Trip trip) {
        List<TripUpdate> updates =
                trip.getTripUpdates().stream()
                        .filter(u -> u.getLocation() != null)
                        .sorted(Comparator.comparing(TripUpdate::getTimestamp))
                        .toList();

        if (updates.isEmpty()) {
            log.warn("Trip {} has no valid location updates", trip.getId());
            return null;
        }

        TripUpdate first = updates.get(0);
        TripUpdate last = updates.get(updates.size() - 1);

        String polyline = trip.getEncodedPolyline() != null ? trip.getEncodedPolyline() : "";

        StringBuilder urlBuilder =
                new StringBuilder("https://maps.googleapis.com/maps/api/staticmap?");
        urlBuilder
                .append("size=")
                .append(thumbnailProperties.getWidth())
                .append("x")
                .append(thumbnailProperties.getHeight());

        urlBuilder
                .append("&markers=color:green|label:A|")
                .append(first.getLocation().getLat())
                .append(",")
                .append(first.getLocation().getLon());

        urlBuilder
                .append("&markers=color:red|label:B|")
                .append(last.getLocation().getLat())
                .append(",")
                .append(last.getLocation().getLon());

        if (!polyline.isEmpty()) {
            urlBuilder.append("&path=color:0x0088ffff|weight:4|enc:").append(polyline);
        }

        urlBuilder.append("&key=").append(googleMapsProperties.getApiKey());

        return urlBuilder.toString();
    }

    private byte[] downloadImage(String urlString) throws IOException {
        URL url = new URL(urlString);
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }

    private Path getStoragePath() {
        return Paths.get(thumbnailProperties.getStoragePath());
    }

    private void ensureStorageDirectoryExists() throws IOException {
        Path storagePath = getStoragePath();
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
            log.info("Created thumbnail storage directory: {}", storagePath.toAbsolutePath());
        }
    }
}
