package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.command.repository.TripDayRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.helper.TripEmbeddedObjectsInitializer;
import com.tomassirio.wanderer.command.service.helper.TripStatusTransitionHandler;
import com.tomassirio.wanderer.commons.domain.ActiveTrip;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDay;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import java.time.Instant;
import java.util.UUID;
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
 * trip status in the database. It also creates system-generated trip updates for lifecycle
 * transitions (TRIP_STARTED, TRIP_ENDED, DAY_START, DAY_END) and manages multi-day trip day
 * tracking. WebSocket broadcasting is handled centrally by {@link
 * com.tomassirio.wanderer.command.websocket.BroadcastableEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripStatusChangedEventHandler implements EventHandler<TripStatusChangedEvent> {

    private final TripRepository tripRepository;
    private final ActiveTripRepository activeTripRepository;
    private final TripUpdateRepository tripUpdateRepository;
    private final TripDayRepository tripDayRepository;
    private final TripEmbeddedObjectsInitializer embeddedObjectsInitializer;
    private final TripStatusTransitionHandler statusTransitionHandler;

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

                            // Create system-generated trip updates for lifecycle transitions
                            createLifecycleTripUpdate(trip, previousStatus, newStatus);

                            // Manage trip day tracking for multi-day trips
                            manageTripDays(trip, previousStatus, newStatus);

                            // Manage active_trips table based on status
                            manageActiveTrip(trip.getUserId(), trip.getId(), newStatus);

                            // No need to call save() - entity is managed and will be flushed
                            // automatically
                            log.info("Trip status changed: {}", event.getTripId());
                        });
    }

    /**
     * Creates system-generated trip updates for significant lifecycle transitions.
     *
     * <ul>
     *   <li>CREATED → IN_PROGRESS: {@link UpdateType#TRIP_STARTED}
     *   <li>IN_PROGRESS → RESTING: {@link UpdateType#DAY_END}
     *   <li>RESTING → IN_PROGRESS: {@link UpdateType#DAY_START}
     *   <li>* → FINISHED: {@link UpdateType#TRIP_ENDED}
     * </ul>
     */
    void createLifecycleTripUpdate(Trip trip, TripStatus previousStatus, TripStatus newStatus) {
        UpdateType updateType = resolveLifecycleUpdateType(previousStatus, newStatus);
        if (updateType == null) {
            return;
        }

        TripUpdate tripUpdate =
                TripUpdate.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .updateType(updateType)
                        .timestamp(Instant.now())
                        .build();

        tripUpdateRepository.save(tripUpdate);
        log.info("Created lifecycle trip update {} for trip {}", updateType, trip.getId());
    }

    /**
     * Determines the {@link UpdateType} for a lifecycle transition, or {@code null} if the
     * transition does not warrant a system-generated update.
     */
    UpdateType resolveLifecycleUpdateType(TripStatus previousStatus, TripStatus newStatus) {
        if (newStatus == TripStatus.IN_PROGRESS && previousStatus == TripStatus.CREATED) {
            return UpdateType.TRIP_STARTED;
        }
        if (newStatus == TripStatus.FINISHED) {
            return UpdateType.TRIP_ENDED;
        }
        if (newStatus == TripStatus.RESTING && previousStatus == TripStatus.IN_PROGRESS) {
            return UpdateType.DAY_END;
        }
        if (newStatus == TripStatus.IN_PROGRESS && previousStatus == TripStatus.RESTING) {
            return UpdateType.DAY_START;
        }
        return null;
    }

    /**
     * Manages trip day records based on status transitions.
     *
     * <ul>
     *   <li>CREATED → IN_PROGRESS: Creates day 1 and sets currentDay = 1
     *   <li>IN_PROGRESS → RESTING: Closes the current day (sets endTimestamp)
     *   <li>RESTING → IN_PROGRESS: Opens a new day (increments currentDay, creates new TripDay)
     *   <li>* → FINISHED: Closes the current day if open
     * </ul>
     */
    void manageTripDays(Trip trip, TripStatus previousStatus, TripStatus newStatus) {
        Instant now = Instant.now();

        if (newStatus == TripStatus.IN_PROGRESS && previousStatus == TripStatus.CREATED) {
            // Trip starting for the first time — create day 1
            trip.getTripDetails().setCurrentDay(1);
            createTripDay(trip, 1, now);
        } else if (newStatus == TripStatus.RESTING && previousStatus == TripStatus.IN_PROGRESS) {
            // Day finished — close the current open day
            closeCurrentTripDay(trip.getId(), now);
        } else if (newStatus == TripStatus.IN_PROGRESS && previousStatus == TripStatus.RESTING) {
            // New day starting — increment currentDay and open a new TripDay
            Integer currentDay = trip.getTripDetails().getCurrentDay();
            int nextDay = (currentDay != null ? currentDay : 0) + 1;
            trip.getTripDetails().setCurrentDay(nextDay);
            createTripDay(trip, nextDay, now);
        } else if (newStatus == TripStatus.FINISHED) {
            // Trip finished — close any open day
            closeCurrentTripDay(trip.getId(), now);
        }
    }

    private void createTripDay(Trip trip, int dayNumber, Instant startTimestamp) {
        TripDay tripDay =
                TripDay.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .dayNumber(dayNumber)
                        .startTimestamp(startTimestamp)
                        .build();
        tripDayRepository.save(tripDay);
        log.debug("Created trip day {} for trip {}", dayNumber, trip.getId());
    }

    private void closeCurrentTripDay(UUID tripId, Instant endTimestamp) {
        tripDayRepository
                .findByTripIdAndEndTimestampIsNull(tripId)
                .ifPresent(
                        tripDay -> {
                            tripDay.setEndTimestamp(endTimestamp);
                            tripDayRepository.save(tripDay);
                            log.debug(
                                    "Closed trip day {} for trip {}",
                                    tripDay.getDayNumber(),
                                    tripId);
                        });
    }

    /**
     * Manages the active_trips table based on trip status changes.
     *
     * <p>Adds a record when status becomes IN_PROGRESS, removes it when status changes from
     * IN_PROGRESS to anything else.
     *
     * @param userId the ID of the user who owns the trip
     * @param tripId the ID of the trip
     * @param newStatus the new status of the trip
     */
    private void manageActiveTrip(UUID userId, UUID tripId, TripStatus newStatus) {
        if (newStatus == TripStatus.IN_PROGRESS) {
            // Add or update active trip record
            ActiveTrip activeTrip =
                    activeTripRepository
                            .findById(userId)
                            .orElse(ActiveTrip.builder().userId(userId).build());
            activeTrip.setTripId(tripId);
            activeTripRepository.save(activeTrip);
            log.debug("Set active trip for user {}: {}", userId, tripId);
        } else {
            // Remove active trip record if exists
            activeTripRepository
                    .findById(userId)
                    .filter(activeTrip -> activeTrip.getTripId().equals(tripId))
                    .ifPresent(
                            activeTrip -> {
                                activeTripRepository.delete(activeTrip);
                                log.debug("Removed active trip for user {}", userId);
                            });
        }
    }
}
