package com.tomassirio.wanderer.command.service.helper;

import com.tomassirio.wanderer.command.repository.TripDayRepository;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDay;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper component responsible for managing trip day records based on status transitions.
 *
 * <ul>
 *   <li>CREATED → IN_PROGRESS: Creates day 1 and sets currentDay = 1
 *   <li>IN_PROGRESS → RESTING: Closes the current day (sets endTimestamp)
 *   <li>RESTING → IN_PROGRESS: Opens a new day (increments currentDay, creates new TripDay)
 *   <li>* → FINISHED: Closes the current day if open
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripDayManager {

    private final TripDayRepository tripDayRepository;

    /**
     * Manages trip day records based on status transitions.
     *
     * @param trip the trip entity (will be mutated to update currentDay)
     * @param previousStatus the previous trip status
     * @param newStatus the new trip status
     */
    public void manageTripDays(Trip trip, TripStatus previousStatus, TripStatus newStatus) {
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
}
