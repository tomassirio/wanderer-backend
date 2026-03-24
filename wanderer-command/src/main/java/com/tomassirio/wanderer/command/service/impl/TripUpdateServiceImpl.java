package com.tomassirio.wanderer.command.service.impl;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.DistanceCalculationStrategy;
import com.tomassirio.wanderer.command.service.GeocodingService;
import com.tomassirio.wanderer.command.service.TripUpdateService;
import com.tomassirio.wanderer.command.service.WeatherService;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Transactional
public class TripUpdateServiceImpl implements TripUpdateService {

    private final TripRepository tripRepository;
    private final TripUpdateRepository tripUpdateRepository;
    private final OwnershipValidator ownershipValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final GeocodingService geocodingService;
    private final WeatherService weatherService;
    private final DistanceCalculationStrategy distanceCalculationStrategy;

    @Override
    public UUID createTripUpdate(UUID userId, UUID tripId, TripUpdateCreationRequest request) {
        Trip trip =
                tripRepository
                        .findById(tripId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        // Pre-generate ID and timestamp
        UUID tripUpdateId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        GeocodingService.GeocodingResult geocodingResult = resolveGeocoding(request.location());
        WeatherService.WeatherResult weatherResult = resolveWeather(request.location());

        // Calculate distance so far
        Double distanceSoFar = calculateDistanceSoFar(trip, request.location());

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(request.location())
                        .batteryLevel(request.battery())
                        .message(request.message())
                        .city(
                                Optional.ofNullable(geocodingResult)
                                        .map(GeocodingService.GeocodingResult::city)
                                        .orElse(null))
                        .country(
                                Optional.ofNullable(geocodingResult)
                                        .map(GeocodingService.GeocodingResult::country)
                                        .orElse(null))
                        .temperatureCelsius(
                                Optional.ofNullable(weatherResult)
                                        .map(WeatherService.WeatherResult::temperatureCelsius)
                                        .orElse(null))
                        .weatherCondition(
                                Optional.ofNullable(weatherResult)
                                        .map(WeatherService.WeatherResult::condition)
                                        .orElse(null))
                        .updateType(request.updateType())
                        .distanceSoFarKm(distanceSoFar)
                        .timestamp(timestamp)
                        .build());

        return tripUpdateId;
    }

    private GeocodingService.GeocodingResult resolveGeocoding(GeoLocation location) {
        return geocodingService.reverseGeocode(location);
    }

    private WeatherService.WeatherResult resolveWeather(GeoLocation location) {
        return weatherService.lookupCurrentWeather(location);
    }

    private Double calculateDistanceSoFar(Trip trip, GeoLocation newLocation) {
        if (newLocation == null || newLocation.getLat() == null || newLocation.getLon() == null) {
            return null;
        }

        double cachedDistance =
                trip.getCachedDistanceKm() != null ? trip.getCachedDistanceKm() : 0.0;

        // Get the last trip update with a valid location
        List<TripUpdate> updates =
                tripUpdateRepository.findByTripIdOrderByTimestampAsc(trip.getId());

        TripUpdate lastUpdate =
                updates.stream()
                        .filter(
                                update ->
                                        update.getLocation() != null
                                                && update.getLocation().getLat() != null
                                                && update.getLocation().getLon() != null)
                        .reduce((first, second) -> second)
                        .orElse(null);

        if (lastUpdate == null) {
            // First update with location
            return cachedDistance;
        }

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
