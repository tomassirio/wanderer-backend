package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.AchievementService;
import com.tomassirio.wanderer.command.service.helper.ActiveTripManager;
import com.tomassirio.wanderer.command.service.helper.LifecycleTripUpdateManager;
import com.tomassirio.wanderer.command.service.helper.TripDayManager;
import com.tomassirio.wanderer.command.service.helper.TripEmbeddedObjectsInitializer;
import com.tomassirio.wanderer.command.service.helper.TripStatusTransitionHandler;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for persisting trip status change events to the database.
 *
 * <p>This handler implements the CQRS write side by handling TripStatusChangedEvent and updating
 * trip status in the database. It delegates lifecycle trip update creation to {@link
 * LifecycleTripUpdateManager}, multi-day trip day tracking to {@link TripDayManager}, and active
 * trip management to {@link ActiveTripManager}. WebSocket broadcasting is handled centrally by
 * {@link com.tomassirio.wanderer.command.websocket.BroadcastableEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripStatusChangedEventHandler implements EventHandler<TripStatusChangedEvent> {

    private final TripRepository tripRepository;
    private final TripEmbeddedObjectsInitializer embeddedObjectsInitializer;
    private final TripStatusTransitionHandler statusTransitionHandler;
    private final LifecycleTripUpdateManager lifecycleTripUpdateManager;
    private final TripDayManager tripDayManager;
    private final ActiveTripManager activeTripManager;
    private final AchievementService achievementService;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TripStatusChangedEvent event) {
        log.debug("Persisting TripStatusChangedEvent for trip: {}", event.getTripId());

        tripRepository
                .findById(event.getTripId())
                .ifPresent(
                        trip -> {
                            TripStatus previousStatus =
                                    event.getPreviousStatus() != null
                                            ? TripStatus.valueOf(event.getPreviousStatus())
                                            : null;
                            TripStatus newStatus = TripStatus.valueOf(event.getNewStatus());

                            embeddedObjectsInitializer.ensureTripSettingsAndGetPreviousStatus(
                                    trip, newStatus);
                            trip.getTripSettings().setTripStatus(newStatus);
                            embeddedObjectsInitializer.ensureTripDetails(trip);
                            statusTransitionHandler.handleStatusTransition(
                                    trip, previousStatus, newStatus);


                            // Manage trip day tracking for multi-day trips
                            tripDayManager.manageTripDays(trip, previousStatus, newStatus);

                            // Manage active_trips table based on status
                            activeTripManager.manageActiveTrip(
                                    trip.getUserId(), trip.getId(), newStatus);

                            // Check and unlock achievements after status change
                            achievementService.checkAndUnlockAchievements(trip.getId());

                            // No need to call save() - entity is managed and will be flushed
                            // automatically
                            log.info("Trip status changed: {}", event.getTripId());
                        });
    }
}
