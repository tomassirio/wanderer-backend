package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.config.properties.GoogleMapsProperties;
import com.tomassirio.wanderer.command.config.properties.ThumbnailProperties;
import com.tomassirio.wanderer.command.service.impl.ThumbnailServiceImpl;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
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
    void generateAndSaveThumbnail_whenThumbnailsDisabled_shouldDoNothing() {
        // Given
        thumbnailProperties.setEnabled(false);
        Trip trip = createTripWithUpdates();

        // When
        thumbnailService.generateAndSaveThumbnail(trip);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_whenGoogleMapsDisabled_shouldDoNothing() {
        // Given
        when(googleMapsProperties.isEnabled()).thenReturn(false);
        Trip trip = createTripWithUpdates();

        // When
        thumbnailService.generateAndSaveThumbnail(trip);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_whenApiKeyNull_shouldDoNothing() {
        // Given
        when(googleMapsProperties.getApiKey()).thenReturn(null);
        Trip trip = createTripWithUpdates();

        // When
        thumbnailService.generateAndSaveThumbnail(trip);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_whenApiKeyEmpty_shouldDoNothing() {
        // Given
        when(googleMapsProperties.getApiKey()).thenReturn("");
        Trip trip = createTripWithUpdates();

        // When
        thumbnailService.generateAndSaveThumbnail(trip);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_whenTripHasNoUpdates_shouldDoNothing() {
        // Given
        Trip trip = createTrip();
        trip.setTripUpdates(null);

        // When
        thumbnailService.generateAndSaveThumbnail(trip);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_whenTripHasEmptyUpdates_shouldDoNothing() {
        // Given
        Trip trip = createTrip();
        trip.setTripUpdates(Collections.emptyList());

        // When
        thumbnailService.generateAndSaveThumbnail(trip);

        // Then - no exception, just returns
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

    // --- TripPlan thumbnail early-exit tests ---

    @Test
    void generateAndSaveThumbnail_tripPlan_whenThumbnailsDisabled_shouldDoNothing() {
        // Given
        thumbnailProperties.setEnabled(false);
        TripPlan tripPlan = createTripPlan();

        // When
        thumbnailService.generateAndSaveThumbnail(tripPlan);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_tripPlan_whenGoogleMapsDisabled_shouldDoNothing() {
        // Given
        when(googleMapsProperties.isEnabled()).thenReturn(false);
        TripPlan tripPlan = createTripPlan();

        // When
        thumbnailService.generateAndSaveThumbnail(tripPlan);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_tripPlan_whenPlannedPolylineIsNull_shouldDoNothing() {
        // Given
        TripPlan tripPlan = createTripPlan();
        tripPlan.setPlannedPolyline(null);

        // When
        thumbnailService.generateAndSaveThumbnail(tripPlan);

        // Then - no exception, just returns
    }

    @Test
    void generateAndSaveThumbnail_tripPlan_whenPlannedPolylineIsEmpty_shouldDoNothing() {
        // Given
        TripPlan tripPlan = createTripPlan();
        tripPlan.setPlannedPolyline("");

        // When
        thumbnailService.generateAndSaveThumbnail(tripPlan);

        // Then - no exception, just returns
    }

    // --- Profile picture overwrite (Files.write overwrites in place) ---

    @Test
    void processAndSaveProfilePicture_whenFileAlreadyExists_shouldOverwriteInPlace()
            throws IOException {
        // Given
        UUID userId = UUID.randomUUID();
        Path profilesDir = tempDir.resolve("profiles");
        Files.createDirectories(profilesDir);
        Path filePath = profilesDir.resolve(userId + ".png");

        // Write initial content
        byte[] oldContent = new byte[] {1, 2, 3};
        Files.write(filePath, oldContent);
        assertThat(Files.exists(filePath)).isTrue();

        byte[] validImage = createTestPngBytes();

        // When
        thumbnailService.processAndSaveProfilePicture(
                userId, validImage, "image/png", "avatar.png");

        // Then - file still exists and content was overwritten
        assertThat(Files.exists(filePath)).isTrue();
        byte[] newContent = Files.readAllBytes(filePath);
        assertThat(newContent).isNotEqualTo(oldContent);
        assertThat(newContent.length).isGreaterThan(0);
    }

    @Test
    void processAndSaveProfilePicture_withValidPng_shouldSaveResizedImage() throws IOException {
        // Given
        UUID userId = UUID.randomUUID();
        byte[] validImage = createTestPngBytes();

        // When
        thumbnailService.processAndSaveProfilePicture(userId, validImage, "image/png", "photo.png");

        // Then
        Path filePath = tempDir.resolve("profiles").resolve(userId + ".png");
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.size(filePath)).isGreaterThan(0);
    }

    @Test
    void processAndSaveProfilePicture_withValidJpeg_shouldSaveImage() throws IOException {
        // Given
        UUID userId = UUID.randomUUID();
        byte[] validImage = createTestPngBytes();

        // When
        thumbnailService.processAndSaveProfilePicture(
                userId, validImage, "image/jpeg", "photo.jpeg");

        // Then
        Path filePath = tempDir.resolve("profiles").resolve(userId + ".png");
        assertThat(Files.exists(filePath)).isTrue();
    }

    @Test
    void processAndSaveProfilePicture_withValidFilenameButNullContentType_shouldSaveImage()
            throws IOException {
        // Given
        UUID userId = UUID.randomUUID();
        byte[] validImage = createTestPngBytes();

        // When
        thumbnailService.processAndSaveProfilePicture(userId, validImage, null, "photo.png");

        // Then
        Path filePath = tempDir.resolve("profiles").resolve(userId + ".png");
        assertThat(Files.exists(filePath)).isTrue();
    }

    @Test
    void processAndSaveProfilePicture_withInvalidContentTypeAndFilename_shouldThrowException() {
        // Given
        UUID userId = UUID.randomUUID();
        byte[] imageBytes = createTestPngBytes();

        // When / Then
        assertThatThrownBy(
                        () ->
                                thumbnailService.processAndSaveProfilePicture(
                                        userId, imageBytes, "application/pdf", "document.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void processAndSaveProfilePicture_withOversizedFile_shouldThrowException() {
        // Given
        UUID userId = UUID.randomUUID();
        byte[] oversizedBytes = new byte[6 * 1024 * 1024]; // 6MB > 5MB limit

        // When / Then
        assertThatThrownBy(
                        () ->
                                thumbnailService.processAndSaveProfilePicture(
                                        userId, oversizedBytes, "image/png", "large.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    // --- Delete and exists for other entity types ---

    @Test
    void deleteThumbnail_forTripPlan_whenFileExists_shouldDeleteFile() throws IOException {
        // Given
        UUID planId = UUID.randomUUID();
        Path plansDir = tempDir.resolve("plans");
        Files.createDirectories(plansDir);
        Path thumbnailPath = plansDir.resolve(planId + ".png");
        Files.createFile(thumbnailPath);

        // When
        thumbnailService.deleteThumbnail(planId, ThumbnailEntityType.TRIP_PLAN);

        // Then
        assertThat(Files.exists(thumbnailPath)).isFalse();
    }

    @Test
    void deleteThumbnail_forUserProfile_whenFileExists_shouldDeleteFile() throws IOException {
        // Given
        UUID userId = UUID.randomUUID();
        Path profilesDir = tempDir.resolve("profiles");
        Files.createDirectories(profilesDir);
        Path thumbnailPath = profilesDir.resolve(userId + ".png");
        Files.createFile(thumbnailPath);

        // When
        thumbnailService.deleteThumbnail(userId, ThumbnailEntityType.USER_PROFILE);

        // Then
        assertThat(Files.exists(thumbnailPath)).isFalse();
    }

    @Test
    void thumbnailExists_forTripPlan_whenFileExists_shouldReturnTrue() throws IOException {
        // Given
        UUID planId = UUID.randomUUID();
        Path plansDir = tempDir.resolve("plans");
        Files.createDirectories(plansDir);
        Files.createFile(plansDir.resolve(planId + ".png"));

        // When
        boolean exists = thumbnailService.thumbnailExists(planId, ThumbnailEntityType.TRIP_PLAN);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void thumbnailExists_forUserProfile_whenFileExists_shouldReturnTrue() throws IOException {
        // Given
        UUID userId = UUID.randomUUID();
        Path profilesDir = tempDir.resolve("profiles");
        Files.createDirectories(profilesDir);
        Files.createFile(profilesDir.resolve(userId + ".png"));

        // When
        boolean exists = thumbnailService.thumbnailExists(userId, ThumbnailEntityType.USER_PROFILE);

        // Then
        assertThat(exists).isTrue();
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

    private TripPlan createTripPlan() {
        GeoLocation start = new GeoLocation();
        start.setLat(42.8782);
        start.setLon(-8.5448);

        GeoLocation end = new GeoLocation();
        end.setLat(42.8843);
        end.setLon(-9.2626);

        return TripPlan.builder()
                .id(UUID.randomUUID())
                .name("Test Plan")
                .planType(TripPlanType.SIMPLE)
                .userId(UUID.randomUUID())
                .createdTimestamp(Instant.now())
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .startLocation(start)
                .endLocation(end)
                .plannedPolyline("encodedPolylineString")
                .build();
    }

    private byte[] createTestPngBytes() {
        try {
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test PNG image", e);
        }
    }
}
