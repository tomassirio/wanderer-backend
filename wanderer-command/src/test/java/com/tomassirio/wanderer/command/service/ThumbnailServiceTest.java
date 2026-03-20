package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.config.properties.GoogleMapsProperties;
import com.tomassirio.wanderer.command.config.properties.ThumbnailProperties;
import com.tomassirio.wanderer.command.service.impl.ThumbnailServiceImpl;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ThumbnailServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class ThumbnailServiceTest {

    @TempDir Path tempDir;

    @Mock private GoogleMapsProperties googleMapsProperties;

    private ThumbnailProperties thumbnailProperties;
    private ThumbnailService thumbnailService;

    @BeforeEach
    void setUp() {
        thumbnailProperties = new ThumbnailProperties();
        thumbnailProperties.setEnabled(true);
        thumbnailProperties.setStoragePath(tempDir.toString());
        thumbnailProperties.setBaseUrl("https://example.com/thumbnails");
        thumbnailProperties.setWidth(600);
        thumbnailProperties.setHeight(338);

        lenient().when(googleMapsProperties.isEnabled()).thenReturn(true);
        lenient().when(googleMapsProperties.getApiKey()).thenReturn("test-api-key");

        thumbnailService = new ThumbnailServiceImpl(thumbnailProperties, googleMapsProperties);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up any created files
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    // Ignore
                                }
                            });
        }
    }

    @Test
    void generateAndSaveThumbnail_whenThumbnailsDisabled_shouldReturnNull() {
        // Given
        thumbnailProperties.setEnabled(false);
        Trip trip = createTripWithUpdates();

        // When
        String result = thumbnailService.generateAndSaveThumbnail(trip);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void generateAndSaveThumbnail_whenGoogleMapsDisabled_shouldReturnNull() {
        // Given
        when(googleMapsProperties.isEnabled()).thenReturn(false);
        Trip trip = createTripWithUpdates();

        // When
        String result = thumbnailService.generateAndSaveThumbnail(trip);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void generateAndSaveThumbnail_whenApiKeyNull_shouldReturnNull() {
        // Given
        when(googleMapsProperties.getApiKey()).thenReturn(null);
        Trip trip = createTripWithUpdates();

        // When
        String result = thumbnailService.generateAndSaveThumbnail(trip);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void generateAndSaveThumbnail_whenApiKeyEmpty_shouldReturnNull() {
        // Given
        when(googleMapsProperties.getApiKey()).thenReturn("");
        Trip trip = createTripWithUpdates();

        // When
        String result = thumbnailService.generateAndSaveThumbnail(trip);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void generateAndSaveThumbnail_whenTripHasNoUpdates_shouldReturnNull() {
        // Given
        Trip trip = createTrip();
        trip.setTripUpdates(null);

        // When
        String result = thumbnailService.generateAndSaveThumbnail(trip);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void generateAndSaveThumbnail_whenTripHasEmptyUpdates_shouldReturnNull() {
        // Given
        Trip trip = createTrip();
        trip.setTripUpdates(Collections.emptyList());

        // When
        String result = thumbnailService.generateAndSaveThumbnail(trip);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void deleteThumbnail_whenFileExists_shouldDeleteFile() throws IOException {
        // Given
        UUID tripId = UUID.randomUUID();
        Path tripsDir = tempDir.resolve("trips");
        Files.createDirectories(tripsDir);
        Path thumbnailPath = tripsDir.resolve(tripId + ".png");
        Files.createFile(thumbnailPath);

        // When
        thumbnailService.deleteThumbnail(tripId, ThumbnailEntityType.TRIP);

        // Then
        assertThat(Files.exists(thumbnailPath)).isFalse();
    }

    @Test
    void deleteThumbnail_whenFileDoesNotExist_shouldNotThrowException() {
        // Given
        UUID tripId = UUID.randomUUID();

        // When / Then - Should not throw exception
        thumbnailService.deleteThumbnail(tripId, ThumbnailEntityType.TRIP);
    }

    @Test
    void thumbnailExists_whenFileExists_shouldReturnTrue() throws IOException {
        // Given
        UUID tripId = UUID.randomUUID();
        Path tripsDir = tempDir.resolve("trips");
        Files.createDirectories(tripsDir);
        Path thumbnailPath = tripsDir.resolve(tripId + ".png");
        Files.createFile(thumbnailPath);

        // When
        boolean exists = thumbnailService.thumbnailExists(tripId, ThumbnailEntityType.TRIP);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void thumbnailExists_whenFileDoesNotExist_shouldReturnFalse() {
        // Given
        UUID tripId = UUID.randomUUID();

        // When
        boolean exists = thumbnailService.thumbnailExists(tripId, ThumbnailEntityType.TRIP);

        // Then
        assertThat(exists).isFalse();
    }

    private Trip createTrip() {
        return Trip.builder().id(UUID.randomUUID()).name("Test Trip").build();
    }

    private Trip createTripWithUpdates() {
        UUID tripId = UUID.randomUUID();

        GeoLocation startLocation = new GeoLocation();
        startLocation.setLat(42.8782);
        startLocation.setLon(-8.5448);

        GeoLocation endLocation = new GeoLocation();
        endLocation.setLat(42.8843);
        endLocation.setLon(-9.2626);

        TripUpdate update1 =
                TripUpdate.builder()
                        .id(UUID.randomUUID())
                        .location(startLocation)
                        .timestamp(Instant.now().minusSeconds(3600))
                        .build();

        TripUpdate update2 =
                TripUpdate.builder()
                        .id(UUID.randomUUID())
                        .location(endLocation)
                        .timestamp(Instant.now())
                        .build();

        List<TripUpdate> updates = new ArrayList<>();
        updates.add(update1);
        updates.add(update2);

        return Trip.builder()
                .id(tripId)
                .name("Test Trip")
                .tripUpdates(updates)
                .encodedPolyline("encodedPolylineString")
                .build();
    }
}
