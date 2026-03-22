package com.tomassirio.wanderer.command.handler;

import static org.mockito.Mockito.verify;

import com.tomassirio.wanderer.command.event.TripPlanDeletedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripPlanDeletedEventHandlerTest {

    @Mock private TripPlanRepository tripPlanRepository;
    @Mock private ThumbnailService thumbnailService;

    @InjectMocks private TripPlanDeletedEventHandler handler;

    @Test
    void handle_shouldDeleteTripPlanAndThumbnail() {
        UUID tripPlanId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TripPlanDeletedEvent event =
                TripPlanDeletedEvent.builder().tripPlanId(tripPlanId).userId(userId).build();

        handler.handle(event);

        verify(tripPlanRepository).deleteById(tripPlanId);
        verify(thumbnailService).deleteThumbnail(tripPlanId, ThumbnailEntityType.TRIP_PLAN);
    }
}
