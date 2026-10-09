package com.tomassirio.wanderer.command.handler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.service.TripUpdateGeocodingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripUpdateEnrichmentEventHandlerTest {

    @Mock private TripUpdateGeocodingService tripUpdateGeocodingService;

    @InjectMocks private TripUpdateEnrichmentEventHandler handler;

    @Test
    void handleTripUpdated_enrichesTheCheckIn() {
        UUID tripUpdateId = UUID.randomUUID();

        handler.handleTripUpdated(TripUpdatedEvent.builder().tripUpdateId(tripUpdateId).build());

        verify(tripUpdateGeocodingService).enrichTripUpdate(tripUpdateId);
    }

    @Test
    void handleTripUpdated_whenEnrichmentFails_doesNotPropagate() {
        UUID tripUpdateId = UUID.randomUUID();
        doThrow(new RuntimeException("Google down"))
                .when(tripUpdateGeocodingService)
                .enrichTripUpdate(tripUpdateId);

        handler.handleTripUpdated(TripUpdatedEvent.builder().tripUpdateId(tripUpdateId).build());

        verify(tripUpdateGeocodingService).enrichTripUpdate(tripUpdateId);
    }
}
