package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.controller.request.TripPlanCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripPlanUpdateRequest;
import com.tomassirio.wanderer.command.event.TripPlanCreatedEvent;
import com.tomassirio.wanderer.command.event.TripPlanDeletedEvent;
import com.tomassirio.wanderer.command.event.TripPlanUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.service.TripPlanMetadataProcessor;
import com.tomassirio.wanderer.command.service.TripPlanService;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.command.service.validator.TripPlanValidator;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Transactional
public class TripPlanServiceImpl implements TripPlanService {

    private final TripPlanRepository tripPlanRepository;
    private final TripPlanMetadataProcessor metadataProcessor;
    private final OwnershipValidator ownershipValidator;
    private final TripPlanValidator tripPlanValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UUID createTripPlan(UUID userId, TripPlanCreationRequest request) {
        // Validate dates
        tripPlanValidator.validateDates(request.startDate(), request.endDate());

        // Pre-generate ID and timestamp
        UUID tripPlanId = UUID.randomUUID();
        Instant createdTimestamp = Instant.now();

        // Process metadata
        Map<String, Object> metadata = new HashMap<>();
        TripPlan tempPlan =
                TripPlan.builder()
                        .id(tripPlanId)
                        .name(request.name())
                        .planType(request.planType())
                        .userId(userId)
                        .createdTimestamp(createdTimestamp)
                        .startDate(request.startDate())
                        .endDate(request.endDate())
                        .startLocation(request.startLocation())
                        .endLocation(request.endLocation())
                        .waypoints(Optional.ofNullable(request.waypoints()).orElse(List.of()))
                        .metadata(metadata)
                        .build();

        metadataProcessor.applyMetadata(tempPlan, metadata);

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripPlanCreatedEvent.builder()
                        .tripPlanId(tripPlanId)
                        .userId(userId)
                        .name(request.name())
                        .planType(request.planType())
                        .startDate(request.startDate())
                        .endDate(request.endDate())
                        .startLocation(request.startLocation())
                        .endLocation(request.endLocation())
                        .waypoints(Optional.ofNullable(request.waypoints()).orElse(List.of()))
                        .metadata(metadata)
                        .createdTimestamp(createdTimestamp)
                        .plannedPolyline(request.plannedPolyline())
                        .build());

        return tripPlanId;
    }

    @Override
    public UUID updateTripPlan(UUID userId, UUID planId, TripPlanUpdateRequest request) {
        TripPlan tripPlan =
                tripPlanRepository
                        .findById(planId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip plan not found"));

        ownershipValidator.validateOwnership(
                tripPlan, userId, TripPlan::getUserId, TripPlan::getId, "trip plan");

        // Publish event - persistence handler will update DB
        eventPublisher.publishEvent(
                TripPlanUpdatedEvent.builder()
                        .tripPlanId(planId)
                        .name(request.name())
                        .startDate(request.startDate())
                        .endDate(request.endDate())
                        .startLocation(request.startLocation())
                        .endLocation(request.endLocation())
                        .waypoints(request.waypoints() != null ? request.waypoints() : List.of())
                        .plannedPolyline(request.plannedPolyline())
                        .build());

        return planId;
    }

    @Override
    public void deleteTripPlan(UUID userId, UUID planId) {
        TripPlan tripPlan =
                tripPlanRepository
                        .findById(planId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip plan not found"));

        ownershipValidator.validateOwnership(
                tripPlan, userId, TripPlan::getUserId, TripPlan::getId, "trip plan");

        // Publish event - persistence handler will delete from DB
        eventPublisher.publishEvent(
                TripPlanDeletedEvent.builder().tripPlanId(planId).userId(userId).build());
    }
}
