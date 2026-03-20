package com.tomassirio.wanderer.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        String expectedUrl = "https://example.com/thumbnails/" + tripId + ".png";
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(thumbnailService.generateAndSaveThumbnail(trip)).thenReturn(expectedUrl);

        TripUpdatedEvent event =
                TripUpdatedEvent.builder().tripId(tripId).tripUpdateId(UUID.randomUUID()).build();

        // When
        eventHandler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verify(thumbnailService).generateAndSaveThumbnail(trip);
    }

    @Test
    void handle_whenThumbnailGenerated_shouldUpdateTripAndSave() {
        // Given
        String expectedUrl = "https://example.com/thumbnails/" + tripId + ".png";
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(thumbnailService.generateAndSaveThumbnail(trip)).thenReturn(expectedUrl);

        TripUpdatedEvent event =
                TripUpdatedEvent.builder().tripId(tripId).tripUpdateId(UUID.randomUUID()).build();

        // When
        eventHandler.handle(event);

        // Then
        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(tripCaptor.capture());

        Trip savedTrip = tripCaptor.getValue();
        assertThat(savedTrip.getThumbnailUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void handle_whenThumbnailGenerationReturnsNull_shouldNotSaveTrip() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(thumbnailService.generateAndSaveThumbnail(trip)).thenReturn(null);

        TripUpdatedEvent event =
                TripUpdatedEvent.builder().tripId(tripId).tripUpdateId(UUID.randomUUID()).build();

        // When
        eventHandler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verify(thumbnailService).generateAndSaveThumbnail(trip);
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void handle_whenTripNotFound_shouldNotGenerateThumbnail() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        TripUpdatedEvent event =
                TripUpdatedEvent.builder().tripId(tripId).tripUpdateId(UUID.randomUUID()).build();

        // When
        eventHandler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verify(thumbnailService, never()).generateAndSaveThumbnail(any(Trip.class));
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void handle_whenThumbnailServiceThrowsException_shouldNotSaveTrip() {
        // Given
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(thumbnailService.generateAndSaveThumbnail(trip))
                .thenThrow(new RuntimeException("Thumbnail generation failed"));

        TripUpdatedEvent event =
                TripUpdatedEvent.builder().tripId(tripId).tripUpdateId(UUID.randomUUID()).build();

        // When
        eventHandler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verify(thumbnailService).generateAndSaveThumbnail(trip);
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void handle_whenRepositoryThrowsException_shouldHandleGracefully() {
        // Given
        when(tripRepository.findById(tripId)).thenThrow(new RuntimeException("Database error"));

        TripUpdatedEvent event =
                TripUpdatedEvent.builder().tripId(tripId).tripUpdateId(UUID.randomUUID()).build();

        // When / Then - Should not throw exception
        eventHandler.handle(event);

        verify(thumbnailService, never()).generateAndSaveThumbnail(any(Trip.class));
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
