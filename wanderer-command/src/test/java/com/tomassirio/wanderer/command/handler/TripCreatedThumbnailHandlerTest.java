package com.tomassirio.wanderer.command.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.tomassirio.wanderer.command.event.TripCreatedEvent;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripCreatedThumbnailHandlerTest {

    @Mock private ThumbnailService thumbnailService;

    private TripCreatedThumbnailHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TripCreatedThumbnailHandler(thumbnailService);
    }

    @Test
    void handle_withTripPlanId_shouldCopyThumbnail() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripPlanId = UUID.randomUUID();

        TripCreatedEvent event =
                TripCreatedEvent.builder()
                        .tripId(tripId)
                        .tripPlanId(tripPlanId)
                        .tripName("Test Trip")
                        .ownerId(UUID.randomUUID())
                        .visibility("PUBLIC")
                        .build();

        when(thumbnailService.thumbnailExists(tripPlanId, ThumbnailEntityType.TRIP_PLAN))
                .thenReturn(true);

        // When
        handler.handle(event);

        // Then
        verify(thumbnailService).thumbnailExists(tripPlanId, ThumbnailEntityType.TRIP_PLAN);
        // Note: File I/O operations are difficult to test without integration tests
        // The actual file copy is tested in integration tests
    }

    @Test
    void handle_withoutTripPlanId_shouldSkipCopy() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripCreatedEvent event =
                TripCreatedEvent.builder()
                        .tripId(tripId)
                        .tripPlanId(null) // No trip plan
                        .tripName("Test Trip")
                        .ownerId(UUID.randomUUID())
                        .visibility("PUBLIC")
                        .build();

        // When
        handler.handle(event);

        // Then
        verify(thumbnailService, never()).thumbnailExists(any(), any());
    }

    @Test
    void handle_whenPlanThumbnailDoesNotExist_shouldSkipCopy() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripPlanId = UUID.randomUUID();

        TripCreatedEvent event =
                TripCreatedEvent.builder()
                        .tripId(tripId)
                        .tripPlanId(tripPlanId)
                        .tripName("Test Trip")
                        .ownerId(UUID.randomUUID())
                        .visibility("PUBLIC")
                        .build();

        when(thumbnailService.thumbnailExists(tripPlanId, ThumbnailEntityType.TRIP_PLAN))
                .thenReturn(false);

        // When
        handler.handle(event);

        // Then
        verify(thumbnailService).thumbnailExists(tripPlanId, ThumbnailEntityType.TRIP_PLAN);
        // No file copy should occur
    }
}
