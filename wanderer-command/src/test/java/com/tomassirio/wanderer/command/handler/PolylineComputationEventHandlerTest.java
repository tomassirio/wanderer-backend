package com.tomassirio.wanderer.command.handler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.tomassirio.wanderer.command.event.TrackPointsRecordedEvent;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.service.PolylineService;
import com.tomassirio.wanderer.command.service.TrackPointService;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PolylineComputationEventHandlerTest {

    @Mock private PolylineService polylineService;

    @Mock private TrackPointService trackPointService;

    @InjectMocks private PolylineComputationEventHandler handler;

    @Test
    void handleTripUpdated_shouldCallAppendSegment() {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(UUID.randomUUID())
                        .tripId(tripId)
                        .location(GeoLocation.builder().lat(42.0).lon(-8.0).build())
                        .batteryLevel(85)
                        .message("Test")
                        .timestamp(Instant.now())
                        .build();

        // When
        handler.handleTripUpdated(event);

        // Then
        verify(polylineService).appendSegment(tripId);
    }

    @Test
    void handleTripUpdated_whenPolylineServiceThrows_shouldNotPropagate() {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(UUID.randomUUID())
                        .tripId(tripId)
                        .location(GeoLocation.builder().lat(42.0).lon(-8.0).build())
                        .timestamp(Instant.now())
                        .build();

        doThrow(new RuntimeException("API failure")).when(polylineService).appendSegment(tripId);

        // When — should not throw
        handler.handleTripUpdated(event);

        // Then
        verify(polylineService).appendSegment(tripId);
    }

    @Test
    void handleTrackPointsRecorded_recomputesTrackWithNewPoints() {
        UUID tripId = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-08T09:15:02Z");
        TripTrackPoint point =
                TripTrackPoint.builder()
                        .id(UUID.randomUUID())
                        .tripId(tripId)
                        .lat(52.09)
                        .lon(5.12)
                        .recordedAt(at)
                        .build();

        handler.handleTrackPointsRecorded(
                TrackPointsRecordedEvent.builder().tripId(tripId).points(List.of(point)).build());

        verify(trackPointService)
                .recomputeTrack(tripId, List.of(new TrackPointDTO(52.09, 5.12, at)));
    }

    @Test
    void handleTrackPointsRecorded_whenRecomputeThrows_shouldNotPropagate() {
        UUID tripId = UUID.randomUUID();
        doThrow(new RuntimeException("db"))
                .when(trackPointService)
                .recomputeTrack(tripId, List.of());

        handler.handleTrackPointsRecorded(
                TrackPointsRecordedEvent.builder().tripId(tripId).points(List.of()).build());

        verify(trackPointService).recomputeTrack(tripId, List.of());
    }
}
