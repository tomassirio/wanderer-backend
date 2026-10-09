package com.tomassirio.wanderer.command.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.controller.request.TrackPointsRequest;
import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.event.TrackPointsRecordedEvent;
import com.tomassirio.wanderer.command.event.TrackUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripTrackPointRepository;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TrackPointServiceImplTest {

    private static final Instant T0 = Instant.parse("2026-10-08T09:00:00Z");

    @Mock private TripRepository tripRepository;
    @Mock private TripTrackPointRepository trackPointRepository;
    @Mock private OwnershipValidator ownershipValidator;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TrackPointServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();

    private Trip givenTrip(TripStatus status, Instant end) {
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(userId)
                        .tripSettings(TripSettings.builder().tripStatus(status).build())
                        .tripDetails(TripDetails.builder().endTimestamp(end).build())
                        .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        lenient().when(trackPointRepository.findExistingIds(anyCollection())).thenReturn(Set.of());
        return trip;
    }

    private static TrackPointsRequest.Point point(UUID id, Instant recordedAt) {
        return new TrackPointsRequest.Point(id, 52.09, 5.12, 8.0, 3.1, recordedAt);
    }

    private TrackPointsRecordedEvent publishedRecordedEvent() {
        ArgumentCaptor<TrackPointsRecordedEvent> captor =
                ArgumentCaptor.forClass(TrackPointsRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest
    @EnumSource(
            value = TripStatus.class,
            names = {"IN_PROGRESS", "RESTING", "PAUSED"})
    void recordTrackPoints_whenTripIsOngoing_acceptsSortedPoints(TripStatus status) {
        givenTrip(status, null);
        TrackPointsRequest.Point later = point(UUID.randomUUID(), T0.plusSeconds(10));
        TrackPointsRequest.Point earlier = point(UUID.randomUUID(), T0);

        int accepted =
                service.recordTrackPoints(
                        userId, tripId, new TrackPointsRequest(List.of(later, earlier)));

        assertThat(accepted).isEqualTo(2);
        TrackPointsRecordedEvent event = publishedRecordedEvent();
        assertThat(event.getTripId()).isEqualTo(tripId);
        assertThat(event.getPoints())
                .extracting(TripTrackPoint::getId)
                .containsExactly(earlier.id(), later.id());
        assertThat(event.getPoints().getFirst().getReceivedAt()).isNotNull();
        assertThat(event.getPoints().getFirst().getTripId()).isEqualTo(tripId);
    }

    @Test
    void recordTrackPoints_ignoresKnownAndDuplicateIds() {
        givenTrip(TripStatus.IN_PROGRESS, null);
        UUID known = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(trackPointRepository.findExistingIds(anyCollection())).thenReturn(Set.of(known));

        int accepted =
                service.recordTrackPoints(
                        userId,
                        tripId,
                        new TrackPointsRequest(
                                List.of(point(known, T0), point(fresh, T0), point(fresh, T0))));

        assertThat(accepted).isEqualTo(1);
        assertThat(publishedRecordedEvent().getPoints())
                .extracting(TripTrackPoint::getId)
                .containsExactly(fresh);
    }

    @Test
    void recordTrackPoints_whenAllKnown_publishesNothing() {
        givenTrip(TripStatus.IN_PROGRESS, null);
        UUID known = UUID.randomUUID();
        when(trackPointRepository.findExistingIds(anyCollection())).thenReturn(Set.of(known));

        assertThat(
                        service.recordTrackPoints(
                                userId, tripId, new TrackPointsRequest(List.of(point(known, T0)))))
                .isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordTrackPoints_whenFinished_keepsOnlyPointsUpToFinishTime() {
        givenTrip(TripStatus.FINISHED, T0);
        TrackPointsRequest.Point before = point(UUID.randomUUID(), T0.minusSeconds(5));
        TrackPointsRequest.Point atEnd = point(UUID.randomUUID(), T0);
        TrackPointsRequest.Point after = point(UUID.randomUUID(), T0.plusSeconds(5));

        int accepted =
                service.recordTrackPoints(
                        userId, tripId, new TrackPointsRequest(List.of(before, atEnd, after)));

        assertThat(accepted).isEqualTo(2);
        assertThat(publishedRecordedEvent().getPoints())
                .extracting(TripTrackPoint::getId)
                .containsExactly(before.id(), atEnd.id());
    }

    @Test
    void recordTrackPoints_whenDraft_isRejected() {
        givenTrip(TripStatus.CREATED, null);

        assertThatThrownBy(
                        () ->
                                service.recordTrackPoints(
                                        userId,
                                        tripId,
                                        new TrackPointsRequest(
                                                List.of(point(UUID.randomUUID(), T0)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATED");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recomputeTrack_storesDistanceAndPolylineAndBroadcasts() {
        Trip trip = Trip.builder().id(tripId).build();
        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        when(trackPointRepository.findByTripIdOrderByRecordedAtAsc(tripId))
                .thenReturn(
                        List.of(
                                stored(52.00, T0),
                                stored(52.01, T0.plusSeconds(60)),
                                stored(52.02, T0.plusSeconds(120))));
        List<TrackPointDTO> fresh = List.of(new TrackPointDTO(52.02, 5.0, T0.plusSeconds(120)));

        service.recomputeTrack(tripId, fresh);

        assertThat(trip.getCachedDistanceKm()).isCloseTo(2.2239, within(0.001));
        assertThat(trip.getEncodedPolyline()).isNotBlank();
        assertThat(trip.getPolylineUpdatedAt()).isNotNull();
        verify(tripRepository).save(trip);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues().get(0))
                .isInstanceOf(PolylineUpdatedEvent.class)
                .hasFieldOrPropertyWithValue("encodedPolyline", trip.getEncodedPolyline())
                .hasFieldOrPropertyWithValue("forceThumbnail", false);
        TrackUpdatedEvent track = (TrackUpdatedEvent) events.getAllValues().get(1);
        assertThat(track.getPoints()).isEqualTo(fresh);
        assertThat(track.getDistanceKm()).isEqualTo(trip.getCachedDistanceKm());
        assertThat(track.getEventType()).isEqualTo("TRACK_UPDATED");
    }

    @Test
    void recomputeTrack_withoutNewPoints_skipsTrackBroadcast() {
        Trip trip = Trip.builder().id(tripId).build();
        when(tripRepository.findByIdForUpdate(tripId)).thenReturn(Optional.of(trip));
        when(trackPointRepository.findByTripIdOrderByRecordedAtAsc(tripId))
                .thenReturn(List.of(stored(52.0, T0)));

        service.recomputeTrack(tripId, List.of());

        assertThat(trip.getEncodedPolyline()).isNull();
        assertThat(trip.getCachedDistanceKm()).isZero();
        verify(eventPublisher)
                .publishEvent(
                        org.mockito.ArgumentMatchers.<PolylineUpdatedEvent>argThat(
                                PolylineUpdatedEvent::isForceThumbnail));
        verify(eventPublisher, never()).publishEvent(any(TrackUpdatedEvent.class));
    }

    @Test
    void distanceAt_returnsNullWithoutTrack() {
        when(trackPointRepository.existsByTripId(tripId)).thenReturn(false);

        assertThat(service.distanceAt(tripId, T0)).isNull();
    }

    @Test
    void distanceAt_sumsPointsUpToInstant() {
        when(trackPointRepository.existsByTripId(tripId)).thenReturn(true);
        when(trackPointRepository.findByTripIdAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
                        tripId, T0))
                .thenReturn(List.of(stored(52.00, T0.minusSeconds(60)), stored(52.01, T0)));

        assertThat(service.distanceAt(tripId, T0)).isCloseTo(1.112, within(0.001));
    }

    private TripTrackPoint stored(double lat, Instant recordedAt) {
        return TripTrackPoint.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .lat(lat)
                .lon(5.0)
                .accuracyM(5.0)
                .recordedAt(recordedAt)
                .build();
    }
}
