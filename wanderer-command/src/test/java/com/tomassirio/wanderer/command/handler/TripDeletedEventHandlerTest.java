package com.tomassirio.wanderer.command.handler;

import static org.mockito.Mockito.verify;

import com.tomassirio.wanderer.command.event.TripDeletedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripDeletedEventHandlerTest {

    @Mock private TripRepository tripRepository;

    @Mock private ThumbnailService thumbnailService;

    @InjectMocks private TripDeletedEventHandler handler;

    @Test
    void handle_whenEventReceived_shouldDeleteTrip() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        TripDeletedEvent event = TripDeletedEvent.builder().tripId(tripId).ownerId(ownerId).build();

        // When
        handler.handle(event);

        // Then
        verify(tripRepository).deleteById(tripId);
        verify(thumbnailService).deleteThumbnail(tripId, ThumbnailEntityType.TRIP);
    }
}
