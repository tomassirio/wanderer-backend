package com.tomassirio.wanderer.command.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripTrackPointRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.RouteService;
import com.tomassirio.wanderer.command.service.TrackPointService;
import com.tomassirio.wanderer.command.service.helper.PolylineCodec;
import com.tomassirio.wanderer.command.service.helper.PolylineComputer;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PolylineServiceImplTest {

    @Mock private TripRepository tripRepository;

    @Mock private TripUpdateRepository tripUpdateRepository;

    @Mock private RouteService routeService;

    @Mock private TripTrackPointRepository trackPointRepository;

    @Mock private TrackPointService trackPointService;

    @Mock private ApplicationEventPublisher eventPublisher;

    private PolylineServiceImpl polylineService;

    @BeforeEach
    void setUp() {
        PolylineComputer polylineComputer = new PolylineComputer(routeService);
        polylineService =
                new PolylineServiceImpl(
                        tripRepository,
                        tripUpdateRepository,
                        trackPointRepository,
                        trackPointService,
                        routeService,
                        polylineComputer,
                        eventPublisher);
    }

    @Test
    void appendSegment_whenTripNotFound_shouldThrowEntityNotFoundException() {
        // Given
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> polylineService.appendSegment(tripId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Trip not found");
    }

    @Test
    void appendSegment_whenFewerThanTwoUpdates_shouldClearPolyline() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .name("Test Trip")
                        .encodedPolyline("existingPolyline")
                        .polylineUpdatedAt(Instant.now())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId)).thenReturn(List.of());

        // When
        polylineService.appendSegment(tripId);

        // Then
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNull();
        assertThat(saved.getPolylineUpdatedAt()).isNull();

        // Verify polyline updated event was published
        ArgumentCaptor<PolylineUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(PolylineUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTripId()).isEqualTo(tripId);
        assertThat(eventCaptor.getValue().getEncodedPolyline()).isNull();
    }

    @Test
    void appendSegment_whenOneUpdate_shouldClearPolyline() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).name("Test Trip").build();

        TripUpdate update =
                createTripUpdate(
                        trip, GeoLocation.builder().lat(42.0).lon(-8.0).build(), Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update));

        // When
        polylineService.appendSegment(tripId);

        // Then
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getEncodedPolyline()).isNull();
    }

    @Test
    void appendSegment_whenExistingPolyline_shouldAppendNewSegment() {
        // Given
        UUID tripId = UUID.randomUUID();

        // Encode 2 existing points to create a realistic existing polyline
        List<LatLng> existingPoints = List.of(new LatLng(42.0, -8.0), new LatLng(42.5, -8.2));
        String existingEncoded = PolylineCodec.encode(existingPoints);

        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .name("Test Trip")
                        .encodedPolyline(existingEncoded)
                        .polylineUpdatedAt(Instant.now().minusSeconds(3600))
                        .build();

        GeoLocation loc1 = GeoLocation.builder().lat(42.0).lon(-8.0).build();
        GeoLocation loc2 = GeoLocation.builder().lat(42.5).lon(-8.2).build();
        GeoLocation loc3 = GeoLocation.builder().lat(43.0).lon(-8.5).build();

        TripUpdate update1 = createTripUpdate(trip, loc1, Instant.now().minusSeconds(7200));
        TripUpdate update2 = createTripUpdate(trip, loc2, Instant.now().minusSeconds(3600));
        TripUpdate update3 = createTripUpdate(trip, loc3, Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update1, update2, update3));

        // New segment from loc2 -> loc3 returns 3 points (first is duplicate)
        List<LatLng> newSegmentPoints =
                List.of(new LatLng(42.5, -8.2), new LatLng(42.7, -8.3), new LatLng(43.0, -8.5));
        when(routeService.getRoutePoints(loc2, loc3)).thenReturn(newSegmentPoints);

        // When
        polylineService.appendSegment(tripId);

        // Then
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNotNull();
        assertThat(saved.getPolylineUpdatedAt()).isNotNull();

        // Decode the saved polyline and verify it has 4 points
        // (2 existing + 2 new, duplicate skipped)
        List<LatLng> decodedResult = PolylineCodec.decode(saved.getEncodedPolyline());
        assertThat(decodedResult).hasSize(4);

        // Verify polyline updated event was published with encoded polyline
        ArgumentCaptor<PolylineUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(PolylineUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTripId()).isEqualTo(tripId);
        assertThat(eventCaptor.getValue().getEncodedPolyline()).isNotNull();
    }

    @Test
    void appendSegment_whenNoExistingPolyline_shouldFullRecompute() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).name("Test Trip").build();

        GeoLocation loc1 = GeoLocation.builder().lat(42.0).lon(-8.0).build();
        GeoLocation loc2 = GeoLocation.builder().lat(42.5).lon(-8.2).build();

        TripUpdate update1 = createTripUpdate(trip, loc1, Instant.now().minusSeconds(3600));
        TripUpdate update2 = createTripUpdate(trip, loc2, Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update1, update2));

        List<LatLng> fullRoute = List.of(new LatLng(42.0, -8.0), new LatLng(42.5, -8.2));
        when(routeService.getFullRoutePoints(List.of(loc1, loc2))).thenReturn(fullRoute);

        // When
        polylineService.appendSegment(tripId);

        // Then
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNotNull();
        assertThat(saved.getPolylineUpdatedAt()).isNotNull();

        List<LatLng> decodedResult = PolylineCodec.decode(saved.getEncodedPolyline());
        assertThat(decodedResult).hasSize(2);
    }

    @Test
    void recomputePolyline_whenTripNotFound_shouldThrowEntityNotFoundException() {
        // Given
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> polylineService.recomputePolyline(tripId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Trip not found");
    }

    @Test
    void recomputePolyline_whenFewerThanTwoUpdates_shouldClearPolyline() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip =
                Trip.builder().id(tripId).name("Test Trip").encodedPolyline("oldPolyline").build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId)).thenReturn(List.of());

        // When
        polylineService.recomputePolyline(tripId);

        // Then
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNull();
        assertThat(saved.getPolylineUpdatedAt()).isNull();
    }

    @Test
    void recomputePolyline_whenMultipleUpdates_shouldComputeFullPolyline() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).name("Test Trip").build();

        GeoLocation loc1 = GeoLocation.builder().lat(42.0).lon(-8.0).build();
        GeoLocation loc2 = GeoLocation.builder().lat(42.5).lon(-8.2).build();
        GeoLocation loc3 = GeoLocation.builder().lat(43.0).lon(-8.5).build();

        TripUpdate update1 = createTripUpdate(trip, loc1, Instant.now().minusSeconds(7200));
        TripUpdate update2 = createTripUpdate(trip, loc2, Instant.now().minusSeconds(3600));
        TripUpdate update3 = createTripUpdate(trip, loc3, Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update1, update2, update3));

        List<LatLng> fullRoutePoints =
                List.of(new LatLng(42.0, -8.0), new LatLng(42.5, -8.2), new LatLng(43.0, -8.5));
        when(routeService.getFullRoutePoints(List.of(loc1, loc2, loc3)))
                .thenReturn(fullRoutePoints);

        // When
        polylineService.recomputePolyline(tripId);

        // Then
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNotNull();
        assertThat(saved.getPolylineUpdatedAt()).isNotNull();

        List<LatLng> decodedResult = PolylineCodec.decode(saved.getEncodedPolyline());
        assertThat(decodedResult).hasSize(3);

        // Verify polyline updated event was published
        ArgumentCaptor<PolylineUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(PolylineUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTripId()).isEqualTo(tripId);
        assertThat(eventCaptor.getValue().getEncodedPolyline()).isNotNull();
    }

    @Test
    void appendSegment_whenEmptyStringPolyline_shouldFullRecompute() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).name("Test Trip").encodedPolyline("").build();

        GeoLocation loc1 = GeoLocation.builder().lat(42.0).lon(-8.0).build();
        GeoLocation loc2 = GeoLocation.builder().lat(42.5).lon(-8.2).build();

        TripUpdate update1 = createTripUpdate(trip, loc1, Instant.now().minusSeconds(3600));
        TripUpdate update2 = createTripUpdate(trip, loc2, Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update1, update2));

        List<LatLng> fullRoute = List.of(new LatLng(42.0, -8.0), new LatLng(42.5, -8.2));
        when(routeService.getFullRoutePoints(List.of(loc1, loc2))).thenReturn(fullRoute);

        // When
        polylineService.appendSegment(tripId);

        // Then — goes through full recompute path (not incremental)
        verify(routeService).getFullRoutePoints(List.of(loc1, loc2));

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNotNull();

        List<LatLng> decodedResult = PolylineCodec.decode(saved.getEncodedPolyline());
        assertThat(decodedResult).hasSize(2);
    }

    private TripUpdate createTripUpdate(Trip trip, GeoLocation location, Instant timestamp) {
        return TripUpdate.builder()
                .id(UUID.randomUUID())
                .trip(trip)
                .location(location)
                .timestamp(timestamp)
                .build();
    }

    // ================================================================
    // Null / incomplete location tests
    // ================================================================

    @Test
    void recomputePolyline_whenUpdatesHaveNullLocations_shouldFilterAndClearPolyline() {
        // Given — 3 updates, but all have null locations → fewer than 2 valid → clear
        UUID tripId = UUID.randomUUID();
        Trip trip =
                Trip.builder().id(tripId).name("Test Trip").encodedPolyline("oldPolyline").build();

        TripUpdate nullLocationUpdate = createTripUpdate(trip, null, Instant.now());
        TripUpdate nullLatUpdate =
                createTripUpdate(
                        trip,
                        GeoLocation.builder().lat(null).lon(-8.0).build(),
                        Instant.now().plusSeconds(1));
        TripUpdate nullLonUpdate =
                createTripUpdate(
                        trip,
                        GeoLocation.builder().lat(42.0).lon(null).build(),
                        Instant.now().plusSeconds(2));

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(nullLocationUpdate, nullLatUpdate, nullLonUpdate));

        // When
        polylineService.recomputePolyline(tripId);

        // Then — polyline cleared, no route service call
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNull();
        assertThat(saved.getPolylineUpdatedAt()).isNull();
    }

    @Test
    void recomputePolyline_whenMixOfValidAndNullLocations_shouldFilterAndCompute() {
        // Given — 4 updates: 2 valid, 2 null → 2 valid locations → compute polyline
        UUID tripId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).name("Test Trip").build();

        GeoLocation validLoc1 = GeoLocation.builder().lat(42.0).lon(-8.0).build();
        GeoLocation validLoc2 = GeoLocation.builder().lat(43.0).lon(-8.5).build();

        TripUpdate validUpdate1 =
                createTripUpdate(trip, validLoc1, Instant.now().minusSeconds(3600));
        TripUpdate nullUpdate = createTripUpdate(trip, null, Instant.now().minusSeconds(1800));
        TripUpdate nullLatUpdate =
                createTripUpdate(
                        trip,
                        GeoLocation.builder().lat(null).lon(-8.2).build(),
                        Instant.now().minusSeconds(900));
        TripUpdate validUpdate2 = createTripUpdate(trip, validLoc2, Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(validUpdate1, nullUpdate, nullLatUpdate, validUpdate2));

        List<LatLng> routePoints = List.of(new LatLng(42.0, -8.0), new LatLng(43.0, -8.5));
        when(routeService.getFullRoutePoints(List.of(validLoc1, validLoc2)))
                .thenReturn(routePoints);

        // When
        polylineService.recomputePolyline(tripId);

        // Then — only 2 valid locations used, polyline computed
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNotNull();
        assertThat(saved.getPolylineUpdatedAt()).isNotNull();

        List<LatLng> decoded = PolylineCodec.decode(saved.getEncodedPolyline());
        assertThat(decoded).hasSize(2);
    }

    @Test
    void appendSegment_whenUpdatesHaveNullLocations_shouldFilterAndClearPolyline() {
        // Given — 3 updates but all have null/incomplete locations
        UUID tripId = UUID.randomUUID();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .name("Test Trip")
                        .encodedPolyline("existingPolyline")
                        .build();

        TripUpdate nullUpdate = createTripUpdate(trip, null, Instant.now());
        TripUpdate nullLatUpdate =
                createTripUpdate(
                        trip,
                        GeoLocation.builder().lat(null).lon(-8.0).build(),
                        Instant.now().plusSeconds(1));
        TripUpdate nullLonUpdate =
                createTripUpdate(
                        trip,
                        GeoLocation.builder().lat(42.0).lon(null).build(),
                        Instant.now().plusSeconds(2));

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(nullUpdate, nullLatUpdate, nullLonUpdate));

        // When
        polylineService.appendSegment(tripId);

        // Then — polyline cleared
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNull();
        assertThat(saved.getPolylineUpdatedAt()).isNull();
    }

    @Test
    void appendSegment_whenMixOfValidAndNullLocations_shouldFilterAndRecompute() {
        // Given — existing polyline but some updates have null locations
        UUID tripId = UUID.randomUUID();

        GeoLocation validLoc1 = GeoLocation.builder().lat(42.0).lon(-8.0).build();
        GeoLocation validLoc2 = GeoLocation.builder().lat(43.0).lon(-8.5).build();

        List<LatLng> existingPoints = List.of(new LatLng(42.0, -8.0));
        String existingEncoded = PolylineCodec.encode(existingPoints);

        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .name("Test Trip")
                        .encodedPolyline(existingEncoded)
                        .polylineUpdatedAt(Instant.now().minusSeconds(3600))
                        .build();

        TripUpdate validUpdate1 =
                createTripUpdate(trip, validLoc1, Instant.now().minusSeconds(7200));
        TripUpdate nullUpdate = createTripUpdate(trip, null, Instant.now().minusSeconds(3600));
        TripUpdate validUpdate2 = createTripUpdate(trip, validLoc2, Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(validUpdate1, nullUpdate, validUpdate2));

        // The two valid locations → incremental append from validLoc1 → validLoc2
        List<LatLng> newSegmentPoints =
                List.of(new LatLng(42.0, -8.0), new LatLng(42.5, -8.3), new LatLng(43.0, -8.5));
        when(routeService.getRoutePoints(validLoc1, validLoc2)).thenReturn(newSegmentPoints);

        // When
        polylineService.appendSegment(tripId);

        // Then — incremental append succeeded despite null update in the middle
        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());

        Trip saved = captor.getValue();
        assertThat(saved.getEncodedPolyline()).isNotNull();
        assertThat(saved.getPolylineUpdatedAt()).isNotNull();
    }

    @Test
    void appendSegment_whenTripHasTrackPoints_skipsDirections() {
        UUID tripId = UUID.randomUUID();
        when(trackPointRepository.existsByTripId(tripId)).thenReturn(true);

        polylineService.appendSegment(tripId);

        verifyNoInteractions(routeService, tripRepository, eventPublisher);
    }

    @Test
    void recomputePolyline_whenTripHasTrackPoints_rebuildsFromTrack() {
        UUID tripId = UUID.randomUUID();
        when(trackPointRepository.existsByTripId(tripId)).thenReturn(true);

        polylineService.recomputePolyline(tripId);

        verify(trackPointService).recomputeTrack(tripId, List.of());
        verifyNoInteractions(routeService);
    }
}
