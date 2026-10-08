package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.service.TripUpdateGeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Asynchronously enriches a check-in with place name and weather once it is committed, so the
 * external API calls never slow down or fail the check-in request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripUpdateEnrichmentEventHandler {

    private final TripUpdateGeocodingService tripUpdateGeocodingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTripUpdated(TripUpdatedEvent event) {
        try {
            tripUpdateGeocodingService.enrichTripUpdate(event.getTripUpdateId());
        } catch (Exception e) {
            log.error(
                    "Failed to enrich trip update {}: {}",
                    event.getTripUpdateId(),
                    e.getMessage(),
                    e);
        }
    }
}
