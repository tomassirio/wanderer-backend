package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripCreatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.helper.TripEmbeddedObjectsInitializer;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for TripCreatedEvent that handles persistence.
 *
 * <p>This handler persists the trip during the transaction. WebSocket broadcasting is handled
 * centrally by {@link com.tomassirio.wanderer.command.websocket.BroadcastableEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripCreatedEventHandler implements EventHandler<TripCreatedEvent> {

    private final TripEmbeddedObjectsInitializer embeddedObjectsInitializer;
    private final TripRepository tripRepository;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TripCreatedEvent event) {
        log.debug("Persisting TripCreatedEvent for trip: {}", event.getTripId());

        Trip trip =
                Trip.builder()
                        .id(event.getTripId())
                        .name(event.getTripName())
                        .userId(event.getOwnerId())
                        .tripSettings(
                                embeddedObjectsInitializer.createTripSettings(
                                        TripVisibility.valueOf(event.getVisibility()),
                                        event.getTripModality()))
                        .tripDetails(
                                event.getStartLocation() != null
                                                || event.getStartTimestamp() != null
                                        ? embeddedObjectsInitializer.createTripDetailsFromEvent(
                                                event.getStartLocation(),
                                                event.getEndLocation(),
                                                event.getWaypoints(),
                                                event.getStartTimestamp(),
                                                event.getEndTimestamp())
                                        : embeddedObjectsInitializer.createTripDetails())
                        .tripPlanId(event.getTripPlanId())
                        .plannedPolyline(event.getPlannedPolyline())
                        .creationTimestamp(event.getCreationTimestamp())
                        .enabled(true)
                        .build();

        tripRepository.save(trip);
        log.info("Trip created and persisted: {}", event.getTripId());
    }
}
