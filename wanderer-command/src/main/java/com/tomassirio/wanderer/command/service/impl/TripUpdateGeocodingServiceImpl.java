package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.event.TripUpdateEnrichedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.GeocodingService;
import com.tomassirio.wanderer.command.service.TripUpdateGeocodingService;
import com.tomassirio.wanderer.command.service.WeatherService;
import com.tomassirio.wanderer.command.service.impl.strategy.HaversineDistanceStrategy;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TripUpdateGeocodingService}: enriches check-ins with place name and
 * weather off the request path, and re-geocodes whole trips on demand.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripUpdateGeocodingServiceImpl implements TripUpdateGeocodingService {

    static final double REUSE_RADIUS_KM = 0.3;
    static final Duration REUSE_WINDOW = Duration.ofMinutes(30);

    private final TripRepository tripRepository;
    private final TripUpdateRepository tripUpdateRepository;
    private final GeocodingService geocodingService;
    private final WeatherService weatherService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void recomputeGeocoding(UUID tripId) {
        tripRepository
                .findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

        List<TripUpdate> updates = tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId);

        for (TripUpdate update : updates) {
            GeocodingService.GeocodingResult result =
                    geocodingService.reverseGeocode(update.getLocation());
            if (result != null) {
                update.setCity(result.city());
                update.setCountry(result.country());
            } else {
                update.setCity(null);
                update.setCountry(null);
            }
        }

        tripUpdateRepository.saveAll(updates);
        log.info("Recomputed geocoding for {} trip updates of trip {}", updates.size(), tripId);
    }

    @Override
    @Transactional
    public void enrichTripUpdate(UUID tripUpdateId) {
        TripUpdate update = tripUpdateRepository.findById(tripUpdateId).orElse(null);
        if (update == null || !hasCoordinates(update.getLocation())) {
            return;
        }
        UUID tripId = update.getTrip().getId();

        Optional<TripUpdate> nearby =
                tripUpdateRepository
                        .findFirstByTripIdAndIdNotAndCityIsNotNullAndTimestampLessThanEqualOrderByTimestampDesc(
                                tripId, tripUpdateId, update.getTimestamp())
                        .filter(previous -> isClose(previous, update));

        if (nearby.isPresent()) {
            TripUpdate previous = nearby.get();
            update.setCity(previous.getCity());
            update.setCountry(previous.getCountry());
            update.setTemperatureCelsius(previous.getTemperatureCelsius());
            update.setWeatherCondition(previous.getWeatherCondition());
        } else {
            GeocodingService.GeocodingResult place =
                    geocodingService.reverseGeocode(update.getLocation());
            WeatherService.WeatherResult weather =
                    weatherService.lookupCurrentWeather(update.getLocation());
            if (place != null) {
                update.setCity(place.city());
                update.setCountry(place.country());
            }
            if (weather != null) {
                update.setTemperatureCelsius(weather.temperatureCelsius());
                update.setWeatherCondition(weather.condition());
            }
        }

        if (update.getCity() == null
                && update.getCountry() == null
                && update.getTemperatureCelsius() == null
                && update.getWeatherCondition() == null) {
            return; // nothing learned, nothing to tell clients
        }

        tripUpdateRepository.save(update);
        eventPublisher.publishEvent(
                TripUpdateEnrichedEvent.builder()
                        .tripId(tripId)
                        .tripUpdateId(tripUpdateId)
                        .city(update.getCity())
                        .country(update.getCountry())
                        .temperatureCelsius(update.getTemperatureCelsius())
                        .weatherCondition(update.getWeatherCondition())
                        .build());
        log.debug("Enriched trip update {} (reused nearby: {})", tripUpdateId, nearby.isPresent());
    }

    private static boolean isClose(TripUpdate previous, TripUpdate current) {
        GeoLocation a = previous.getLocation();
        GeoLocation b = current.getLocation();
        return hasCoordinates(a)
                && Duration.between(previous.getTimestamp(), current.getTimestamp())
                                .compareTo(REUSE_WINDOW)
                        <= 0
                && HaversineDistanceStrategy.haversineKm(
                                a.getLat(), a.getLon(), b.getLat(), b.getLon())
                        <= REUSE_RADIUS_KM;
    }

    private static boolean hasCoordinates(GeoLocation location) {
        return location != null && location.getLat() != null && location.getLon() != null;
    }
}
