package com.tomassirio.wanderer.command.service.impl;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.DistanceCalculationStrategy;
import com.tomassirio.wanderer.command.service.TripUpdateService;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
@Transactional
public class TripUpdateServiceImpl implements TripUpdateService {

    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final TripRepository tripRepository;
    private final TripUpdateRepository tripUpdateRepository;
    private final OwnershipValidator ownershipValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final DistanceCalculationStrategy distanceCalculationStrategy;

    @Override
    public UUID createTripUpdate(UUID userId, UUID tripId, TripUpdateCreationRequest request) {
        Trip trip =
                tripRepository
                        .findById(tripId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        // Safe retry from the phone's offline queue: the check-in already landed.
        if (request.id() != null && tripUpdateRepository.existsById(request.id())) {
            return request.id();
        }

        // Every check-in (manual, automatic, lifecycle marker) goes through here, so this is the
        // one place that keeps Drafts and ended trips from collecting check-ins.
        TripStatus status =
                trip.getTripSettings() != null ? trip.getTripSettings().getTripStatus() : null;
        if (status == null || !status.acceptsCheckIn(request.updateType())) {
            throw new IllegalStateException(
                    "Check-ins are not allowed for a trip in status " + status);
        }

        UpdateType updateType = request.updateType();
        GeoLocation location = request.location();
        if ((updateType == null || updateType == UpdateType.REGULAR) && !hasCoordinates(location)) {
            throw new IllegalArgumentException("Location is required for REGULAR check-ins");
        }

        Instant now = Instant.now();
        if (request.recordedAt() != null
                && request.recordedAt().isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw new IllegalArgumentException("recordedAt must not be in the future");
        }

        UUID tripUpdateId = request.id() != null ? request.id() : UUID.randomUUID();
        Instant timestamp = request.recordedAt() != null ? request.recordedAt() : now;

        Double distanceSoFar = calculateDistanceSoFar(trip, location);

        log.debug(
                "Trip update for trip {}: calculated distanceSoFar = {} km", tripId, distanceSoFar);

        // City and weather are filled in asynchronously after commit (TRIP_UPDATE_ENRICHED), so
        // the request never waits on Google.
        eventPublisher.publishEvent(
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(request.battery())
                        .message(request.message())
                        .updateType(updateType)
                        .distanceSoFarKm(distanceSoFar)
                        .timestamp(timestamp)
                        .build());

        return tripUpdateId;
    }

    private static boolean hasCoordinates(GeoLocation location) {
        return location != null && location.getLat() != null && location.getLon() != null;
    }

    private Double calculateDistanceSoFar(Trip trip, GeoLocation newLocation) {
        if (newLocation == null || newLocation.getLat() == null || newLocation.getLon() == null) {
            return null;
        }

        double cachedDistance =
                trip.getCachedDistanceKm() != null ? trip.getCachedDistanceKm() : 0.0;

        // Get only the last trip update with a valid location (efficient query)
        Optional<TripUpdate> lastUpdateOpt =
                tripUpdateRepository.findFirstByTripIdAndLocationIsNotNullOrderByTimestampDesc(
                        trip.getId());

        if (lastUpdateOpt.isEmpty()) {
            // First update with location
            return cachedDistance;
        }

        TripUpdate lastUpdate = lastUpdateOpt.get();

        // Calculate distance from last point to new point
        List<LatLng> segment =
                List.of(
                        new LatLng(
                                lastUpdate.getLocation().getLat(),
                                lastUpdate.getLocation().getLon()),
                        new LatLng(newLocation.getLat(), newLocation.getLon()));

        double segmentDistance = distanceCalculationStrategy.calculatePathDistance(segment);
        double totalDistance = cachedDistance + segmentDistance;

        // Update the cached distance on the trip
        trip.setCachedDistanceKm(totalDistance);
        tripRepository.save(trip);

        return totalDistance;
    }
}
