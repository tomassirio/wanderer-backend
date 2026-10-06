package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.analytics.TripStartFunnel;
import com.tomassirio.wanderer.command.controller.request.StartTripRequest;
import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripFromPlanRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateRequest;
import com.tomassirio.wanderer.command.event.TripCreatedEvent;
import com.tomassirio.wanderer.command.event.TripDeletedEvent;
import com.tomassirio.wanderer.command.event.TripMetadataUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripSettingsUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.event.TripVisibilityChangedEvent;
import com.tomassirio.wanderer.command.handler.support.AfterCommit;
import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.command.service.TripService;
import com.tomassirio.wanderer.command.service.TripUpdateService;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.dto.StartTripResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripPlanRepository tripPlanRepository;
    private final ActiveTripRepository activeTripRepository;
    private final OwnershipValidator ownershipValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final TripUpdateService tripUpdateService;
    private final TripUpdateRepository tripUpdateRepository;
    private final TripStartFunnel tripStartFunnel;

    private static final Integer DEFAULT_UPDATE_REFRESH_SECONDS = 900;

    @Override
    public UUID createTrip(UUID ownerId, TripCreationRequest request) {
        requireUser(ownerId);
        return publishTripCreated(
                ownerId,
                request.name(),
                request.visibility(),
                request.tripModality(),
                request.automaticUpdates(),
                request.updateRefresh(),
                null,
                null);
    }

    @Override
    public StartTripResponse startTrip(
            UUID userId, String idempotencyKey, StartTripRequest request) {
        Optional<Trip> previous =
                tripRepository.findByUserIdAndStartIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            Trip trip = previous.get();
            UUID firstCheckIn =
                    tripUpdateRepository
                            .findFirstByTripIdAndUpdateTypeOrderByTimestampAsc(
                                    trip.getId(), UpdateType.TRIP_STARTED)
                            .map(TripUpdate::getId)
                            .orElse(null);
            return new StartTripResponse(
                    trip.getId(), firstCheckIn, trip.getTripSettings().getTripStatus(), true);
        }

        requireUser(userId);
        TripPlan plan =
                request.tripPlanId() != null
                        ? requireOwnedPlan(userId, request.tripPlanId())
                        : null;
        Integer updateRefresh =
                request.updateRefresh() == null && Boolean.TRUE.equals(request.automaticUpdates())
                        ? DEFAULT_UPDATE_REFRESH_SECONDS
                        : request.updateRefresh();

        // Create, go live and check in within this one transaction: any failure rolls back all
        // three, so no Draft is ever committed.
        UUID tripId =
                publishTripCreated(
                        userId,
                        request.name(),
                        request.visibility(),
                        // From a plan the type is the plan's; from scratch it defaults to SIMPLE
                        plan != null
                                ? null
                                : Optional.ofNullable(request.tripModality())
                                        .orElse(TripModality.SIMPLE),
                        request.automaticUpdates(),
                        updateRefresh,
                        plan,
                        idempotencyKey);
        changeStatus(userId, tripId, TripStatus.IN_PROGRESS);
        UUID tripUpdateId =
                tripUpdateService.createTripUpdate(
                        userId,
                        tripId,
                        new TripUpdateCreationRequest(
                                request.location(),
                                request.battery(),
                                request.message() != null ? request.message() : "Trip Started!",
                                UpdateType.TRIP_STARTED));

        AfterCommit.run(
                () ->
                        tripStartFunnel.record(
                                TripStartFunnel.Event.TRIP_STARTED,
                                plan != null
                                        ? TripStartFunnel.Source.PLAN
                                        : TripStartFunnel.Source.SCRATCH));
        return new StartTripResponse(tripId, tripUpdateId, TripStatus.IN_PROGRESS, false);
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
    public void adminDeleteTrip(UUID adminId, UUID id) {
        // Validate trip exists (no ownership check - admin can delete any trip)
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        log.info("Admin {} deleting trip {} owned by {}", adminId, id, trip.getUserId());

        // Publish the same event the owner-driven delete uses, with the trip's actual owner
        eventPublisher.publishEvent(
                TripDeletedEvent.builder().tripId(id).ownerId(trip.getUserId()).build());
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
    public UUID createTripFromPlan(UUID userId, UUID tripPlanId, TripFromPlanRequest request) {
        requireUser(userId);
        TripPlan tripPlan = requireOwnedPlan(userId, tripPlanId);
        return publishTripCreated(
                userId,
                null,
                request.visibility(),
                request.tripModality(),
                request.automaticUpdates(),
                request.updateRefresh(),
                tripPlan,
                null);
    }

    private void requireUser(UUID userId) {
        userRepository
                .findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private TripPlan requireOwnedPlan(UUID userId, UUID tripPlanId) {
        TripPlan tripPlan =
                tripPlanRepository
                        .findById(tripPlanId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip plan not found"));
        ownershipValidator.validateOwnership(
                tripPlan, userId, TripPlan::getUserId, TripPlan::getId, "trip plan");
        return tripPlan;
    }

    /**
     * Publishes a {@link TripCreatedEvent} for a new trip (status CREATED). With a plan, the trip
     * takes the plan's name, route and dates; the modality is the requested one, or derived from
     * the plan type when absent.
     */
    private UUID publishTripCreated(
            UUID ownerId,
            String name,
            TripVisibility visibility,
            TripModality modality,
            Boolean automaticUpdates,
            Integer updateRefresh,
            TripPlan plan,
            String idempotencyKey) {
        UUID tripId = UUID.randomUUID();
        TripCreatedEvent.TripCreatedEventBuilder event =
                TripCreatedEvent.builder()
                        .tripId(tripId)
                        .tripName(name)
                        .ownerId(ownerId)
                        .visibility(visibility.name())
                        .creationTimestamp(Instant.now())
                        .tripModality(modality)
                        .automaticUpdates(automaticUpdates)
                        .updateRefresh(updateRefresh)
                        .startIdempotencyKey(idempotencyKey);
        if (plan != null) {
            event.tripName(plan.getName())
                    .tripPlanId(plan.getId())
                    .startLocation(plan.getStartLocation())
                    .endLocation(plan.getEndLocation())
                    .waypoints(plan.getWaypoints() != null ? plan.getWaypoints() : List.of())
                    .startTimestamp(plan.getStartDate().atStartOfDay().toInstant(ZoneOffset.UTC))
                    .endTimestamp(plan.getEndDate().atStartOfDay().toInstant(ZoneOffset.UTC))
                    .tripModality(
                            modality != null
                                    ? modality
                                    : deriveModalityFromPlanType(plan.getPlanType()))
                    .plannedPolyline(plan.getPlannedPolyline());
        }
        eventPublisher.publishEvent(event.build());
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
