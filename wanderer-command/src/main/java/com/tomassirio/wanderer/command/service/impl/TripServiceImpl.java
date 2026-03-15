package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateRequest;
import com.tomassirio.wanderer.command.event.TripCreatedEvent;
import com.tomassirio.wanderer.command.event.TripDeletedEvent;
import com.tomassirio.wanderer.command.event.TripMetadataUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripSettingsUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.event.TripVisibilityChangedEvent;
import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.command.service.TripService;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripPlanRepository tripPlanRepository;
    private final ActiveTripRepository activeTripRepository;
    private final OwnershipValidator ownershipValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UUID createTrip(UUID ownerId, TripCreationRequest request) {
        // Validate user exists
        userRepository
                .findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Pre-generate ID for the trip
        UUID tripId = UUID.randomUUID();
        Instant creationTimestamp = Instant.now();

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripCreatedEvent.builder()
                        .tripId(tripId)
                        .tripName(request.name())
                        .ownerId(ownerId)
                        .visibility(request.visibility().name())
                        .tripPlanId(null)
                        .creationTimestamp(creationTimestamp)
                        .tripModality(request.tripModality())
                        .automaticUpdates(request.automaticUpdates())
                        .updateRefresh(request.updateRefresh())
                        .build());

        return tripId;
    }

    @Override
    public UUID updateTrip(UUID userId, UUID id, TripUpdateRequest request) {
        // Validate trip exists and ownership
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripMetadataUpdatedEvent.builder()
                        .tripId(id)
                        .tripName(request.name())
                        .visibility(request.visibility().name())
                        .build());

        return id;
    }

    @Override
    public void deleteTrip(UUID userId, UUID id) {
        // Validate trip exists and ownership
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        // Publish event - persistence handler will delete from DB
        eventPublisher.publishEvent(TripDeletedEvent.builder().tripId(id).ownerId(userId).build());
    }

    @Override
    public UUID changeVisibility(UUID userId, UUID id, TripVisibility visibility) {
        // Validate trip exists and ownership
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        TripVisibility previousVisibility =
                trip.getTripSettings() != null ? trip.getTripSettings().getVisibility() : null;

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripVisibilityChangedEvent.builder()
                        .tripId(id)
                        .newVisibility(visibility.name())
                        .previousVisibility(
                                previousVisibility != null ? previousVisibility.name() : null)
                        .build());

        return id;
    }

    @Override
    public UUID changeStatus(UUID userId, UUID id, TripStatus status) {
        // Validate trip exists and ownership
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        TripStatus previousStatus =
                trip.getTripSettings() != null ? trip.getTripSettings().getTripStatus() : null;

        // Validate allowed status transition
        if (previousStatus != null && !previousStatus.canTransitionTo(status)) {
            throw new IllegalStateException(
                    "Cannot transition from " + previousStatus + " to " + status + ".");
        }

        // RESTING is only valid for MULTI_DAY trips — use toggle-day endpoint instead
        if (status == TripStatus.RESTING) {
            TripModality modality =
                    Optional.ofNullable(trip.getTripSettings())
                            .map(TripSettings::getTripModality)
                            .orElse(null);
            if (modality != TripModality.MULTI_DAY) {
                throw new IllegalStateException(
                        "RESTING status is only available for MULTI_DAY trips.");
            }
        }

        // Validate that user doesn't have another trip in progress
        if (status == TripStatus.IN_PROGRESS) {
            activeTripRepository
                    .findById(userId)
                    .ifPresent(
                            activeTrip -> {
                                if (!activeTrip.getTripId().equals(id)) {
                                    throw new IllegalStateException(
                                            "User already has a trip in progress. Only one trip can be in progress at a time.");
                                }
                            });
        }

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripStatusChangedEvent.builder()
                        .tripId(id)
                        .newStatus(status.name())
                        .previousStatus(previousStatus != null ? previousStatus.name() : null)
                        .build());

        return id;
    }

    @Override
    public UUID createTripFromPlan(UUID userId, UUID tripPlanId, TripVisibility visibility) {
        // Validate user exists
        userRepository
                .findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Fetch and validate trip plan ownership
        TripPlan tripPlan =
                tripPlanRepository
                        .findById(tripPlanId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip plan not found"));

        ownershipValidator.validateOwnership(
                tripPlan, userId, TripPlan::getUserId, TripPlan::getId, "trip plan");

        // Pre-generate ID and timestamp
        UUID tripId = UUID.randomUUID();
        Instant creationTimestamp = Instant.now();

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripCreatedEvent.builder()
                        .tripId(tripId)
                        .tripName(tripPlan.getName())
                        .ownerId(userId)
                        .visibility(visibility.name())
                        .tripPlanId(tripPlanId)
                        .creationTimestamp(creationTimestamp)
                        .startLocation(tripPlan.getStartLocation())
                        .endLocation(tripPlan.getEndLocation())
                        .waypoints(
                                tripPlan.getWaypoints() != null
                                        ? tripPlan.getWaypoints()
                                        : List.of())
                        .startTimestamp(
                                tripPlan.getStartDate().atStartOfDay().toInstant(ZoneOffset.UTC))
                        .endTimestamp(
                                tripPlan.getEndDate().atStartOfDay().toInstant(ZoneOffset.UTC))
                        .tripModality(deriveModalityFromPlanType(tripPlan.getPlanType()))
                        .plannedPolyline(tripPlan.getPlannedPolyline())
                        .build());

        return tripId;
    }

    @Override
    public UUID updateSettings(
            UUID userId,
            UUID id,
            Integer updateRefresh,
            Boolean automaticUpdates,
            TripModality tripModality) {
        // Validate trip exists and ownership
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        validateModalityTransition(trip, tripModality);

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripSettingsUpdatedEvent.builder()
                        .tripId(id)
                        .updateRefresh(updateRefresh)
                        .automaticUpdates(automaticUpdates)
                        .tripModality(tripModality)
                        .build());

        return id;
    }

    private TripModality deriveModalityFromPlanType(TripPlanType planType) {
        return TripPlanType.MULTI_DAY.equals(planType)
                ? TripModality.MULTI_DAY
                : TripModality.SIMPLE;
    }

    /**
     * Validates that the requested modality transition is permitted. A trip can be upgraded from
     * SIMPLE to MULTI_DAY, but the reverse downgrade is not allowed.
     */
    private void validateModalityTransition(Trip trip, TripModality newModality) {
        if (newModality == null) {
            return;
        }
        TripModality currentModality =
                Optional.ofNullable(trip.getTripSettings())
                        .map(TripSettings::getTripModality)
                        .orElse(null);
        if (currentModality == TripModality.MULTI_DAY && newModality == TripModality.SIMPLE) {
            throw new IllegalStateException(
                    "Trip modality cannot be downgraded from MULTI_DAY to SIMPLE.");
        }
    }
}
