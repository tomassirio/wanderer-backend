package com.tomassirio.wanderer.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link TripThumbnailEventHandler}. */
@ExtendWith(MockitoExtension.class)
class TripThumbnailEventHandlerTest {

    @Mock private TripRepository tripRepository;

    @Mock private ThumbnailService thumbnailService;

    @InjectMocks private TripThumbnailEventHandler eventHandler;

    private UUID tripId;
    private Trip trip;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        trip = createTripWithUpdates(tripId);
    }

    @Test
    void handle_whenTripExists_shouldGenerateThumbnail() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        PolylineUpdatedEvent event =
                PolylineUpdatedEvent.builder().tripId(tripId).encodedPolyline("abc").build();

        // When
        eventHandler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verify(thumbnailService).generateAndSaveThumbnail(trip);
    }

    @Test
    void handle_whenPolylineCleared_shouldStillGenerateThumbnail() {
        // A trip with a single location clears its polyline; the thumbnail (markers only)
        // must still be generated.
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        eventHandler.handle(PolylineUpdatedEvent.builder().tripId(tripId).build());

        verify(thumbnailService).generateAndSaveThumbnail(trip);
    }

    @Test
    void handle_whenThumbnailGenerated_shouldNotUpdateTrip() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        PolylineUpdatedEvent event =
                PolylineUpdatedEvent.builder().tripId(tripId).encodedPolyline("abc").build();

        // When
        eventHandler.handle(event);

        // Then
        verify(thumbnailService).generateAndSaveThumbnail(trip);
        // Trip is no longer saved since thumbnail URL is not stored in DB
        verify(tripRepository, never()).saveAndFlush(any());
    }

    @Test
    void handle_whenThumbnailGenerationCompletes_shouldNotSaveTrip() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        PolylineUpdatedEvent event =
                PolylineUpdatedEvent.builder().tripId(tripId).encodedPolyline("abc").build();

        // When
        eventHandler.handle(event);
        eventHandler.handle(event);

        // Then
        verify(tripRepository, org.mockito.Mockito.times(2)).findById(tripId);
        verify(thumbnailService, org.mockito.Mockito.times(2)).generateAndSaveThumbnail(trip);
        verify(tripRepository, never()).saveAndFlush(any(Trip.class));
    }

    @Test
    void handle_whenTripNotFound_shouldLogError() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        PolylineUpdatedEvent event =
                PolylineUpdatedEvent.builder().tripId(tripId).encodedPolyline("abc").build();

        // When - should not throw exception, just log error
        eventHandler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verify(thumbnailService, never()).generateAndSaveThumbnail(any(Trip.class));
        verify(tripRepository, never()).saveAndFlush(any(Trip.class));
    }

    @Test
    void handle_whenThumbnailServiceThrowsException_shouldNotPropagateException() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doThrow(new RuntimeException("Thumbnail generation failed"))
                .when(thumbnailService)
                .generateAndSaveThumbnail(trip);

        PolylineUpdatedEvent event =
                PolylineUpdatedEvent.builder().tripId(tripId).encodedPolyline("abc").build();

        // When - should not throw since we catch exceptions now
        eventHandler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verify(thumbnailService).generateAndSaveThumbnail(trip);
        verify(tripRepository, never()).saveAndFlush(any(Trip.class));
    }

    @Test
    void handle_whenRepositoryThrowsException_shouldLogError() {
        // Given
        when(tripRepository.findById(tripId)).thenThrow(new RuntimeException("Database error"));

        PolylineUpdatedEvent event =
                PolylineUpdatedEvent.builder().tripId(tripId).encodedPolyline("abc").build();

        // When - should not throw exception, just log error
        eventHandler.handle(event);

        // Then
        verify(thumbnailService, never()).generateAndSaveThumbnail(any(Trip.class));
    }

    @Test
    void handle_whenThumbnailRefreshedRecently_shouldSkip() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        givenThumbnailAge(java.time.Duration.ofMinutes(5));

        eventHandler.handle(PolylineUpdatedEvent.builder().tripId(tripId).build());

        verify(thumbnailService, never()).generateAndSaveThumbnail(any(Trip.class));
    }

    @Test
    void handle_whenThumbnailOlderThanInterval_shouldRegenerate() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        givenThumbnailAge(TripThumbnailEventHandler.THUMBNAIL_REFRESH_INTERVAL.plusMinutes(1));

        eventHandler.handle(PolylineUpdatedEvent.builder().tripId(tripId).build());

        verify(thumbnailService).generateAndSaveThumbnail(trip);
    }

    @Test
    void handle_whenTripFinished_shouldIgnoreThrottle() {
        trip.setTripSettings(TripSettings.builder().tripStatus(TripStatus.FINISHED).build());
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        eventHandler.handle(PolylineUpdatedEvent.builder().tripId(tripId).build());

        verify(thumbnailService).generateAndSaveThumbnail(trip);
        verify(thumbnailService, never()).thumbnailLastModified(any(), any());
    }

    @Test
    void handle_whenForced_shouldIgnoreThrottle() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        eventHandler.handle(
                PolylineUpdatedEvent.builder().tripId(tripId).forceThumbnail(true).build());

        verify(thumbnailService).generateAndSaveThumbnail(trip);
        verify(thumbnailService, never()).thumbnailLastModified(any(), any());
    }

    @Test
    void handleTripFinished_shouldRegenerateWithoutThrottle() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        eventHandler.handleTripFinished(
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus(TripStatus.IN_PROGRESS.name())
                        .newStatus(TripStatus.FINISHED.name())
                        .build());

        verify(thumbnailService).generateAndSaveThumbnail(trip);
        verify(thumbnailService, never()).thumbnailLastModified(any(), any());
    }

    @Test
    void handleTripFinished_whenOtherStatus_shouldDoNothing() {
        eventHandler.handleTripFinished(
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus(TripStatus.IN_PROGRESS.name())
                        .newStatus(TripStatus.PAUSED.name())
                        .build());

        verifyNoInteractions(tripRepository, thumbnailService);
    }

    private void givenThumbnailAge(java.time.Duration age) {
        when(thumbnailService.thumbnailLastModified(tripId, ThumbnailEntityType.TRIP))
                .thenReturn(Optional.of(Instant.now().minus(age)));
    }

    private Trip createTripWithUpdates(UUID tripId) {
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

    private static org.assertj.core.api.AbstractStringAssert<?> assertThat(String actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
