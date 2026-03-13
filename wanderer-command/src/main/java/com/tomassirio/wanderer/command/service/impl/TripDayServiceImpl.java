package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.TripDayService;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TripDayServiceImpl implements TripDayService {

    private final TripRepository tripRepository;
    private final ActiveTripRepository activeTripRepository;
    private final OwnershipValidator ownershipValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UUID toggleDay(UUID userId, UUID id) {
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        TripModality modality =
                Optional.ofNullable(trip.getTripSettings())
                        .map(TripSettings::getTripModality)
                        .orElse(null);

        if (modality != TripModality.MULTI_DAY) {
            throw new IllegalStateException("Toggle day is only available for MULTI_DAY trips.");
        }

        TripStatus currentStatus =
                Optional.ofNullable(trip.getTripSettings())
                        .map(TripSettings::getTripStatus)
                        .orElse(null);

        TripStatus newStatus;
        if (currentStatus == TripStatus.IN_PROGRESS) {
            newStatus = TripStatus.RESTING;
        } else if (currentStatus == TripStatus.RESTING) {
            newStatus = TripStatus.IN_PROGRESS;
            activeTripRepository
                    .findById(userId)
                    .ifPresent(
                            activeTrip -> {
                                if (!activeTrip.getTripId().equals(id)) {
                                    throw new IllegalStateException(
                                            "User already has a trip in progress. Only one trip can be in progress at a time.");
                                }
                            });
        } else {
            throw new IllegalStateException(
                    "Toggle day requires trip to be IN_PROGRESS or RESTING. Current status: "
                            + currentStatus);
        }

        eventPublisher.publishEvent(
                TripStatusChangedEvent.builder()
                        .tripId(id)
                        .newStatus(newStatus.name())
                        .previousStatus(currentStatus.name())
                        .build());

        return id;
    }
}
