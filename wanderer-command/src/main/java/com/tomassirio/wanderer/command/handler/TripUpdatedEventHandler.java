package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.AchievementService;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for persisting trip update events to the database.
 *
 * <p>This handler implements the CQRS write side by handling TripUpdatedEvent and persisting trip
 * updates to the database. Validation is performed in the service layer before the event is
 * emitted. WebSocket broadcasting is handled centrally by {@link
 * com.tomassirio.wanderer.command.websocket.BroadcastableEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripUpdatedEventHandler implements EventHandler<TripUpdatedEvent> {

    private final TripRepository tripRepository;
    private final TripUpdateRepository tripUpdateRepository;
    private final AchievementService achievementCalculationService;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TripUpdatedEvent event) {
        log.debug("Persisting TripUpdatedEvent for trip: {}", event.getTripId());

        // Fetch trip to increment updateCount
        Trip trip =
                tripRepository
                        .findById(event.getTripId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Trip not found: " + event.getTripId()));

        TripUpdate tripUpdate =
                TripUpdate.builder()
                        .id(event.getTripUpdateId())
                        .trip(trip)
                        .location(event.getLocation())
                        .battery(event.getBatteryLevel())
                        .message(event.getMessage())
                        .city(event.getCity())
                        .country(event.getCountry())
                        .temperatureCelsius(event.getTemperatureCelsius())
                        .weatherCondition(event.getWeatherCondition())
                        .updateType(event.getUpdateType())
                        .distanceSoFarKm(event.getDistanceSoFarKm())
                        .timestamp(event.getTimestamp())
                        .build();

        tripUpdateRepository.save(tripUpdate);

        // Increment updateCount for cache-busting and thumbnail logic
        trip.incrementUpdateCount();
        tripRepository.save(trip);

        log.info(
                "Trip update created and persisted: {} (updateCount: {})",
                event.getTripUpdateId(),
                trip.getUpdateCount());

        // Check and unlock achievements after persisting the update
        achievementCalculationService.checkAndUnlockAchievements(event.getTripId());
    }
}
