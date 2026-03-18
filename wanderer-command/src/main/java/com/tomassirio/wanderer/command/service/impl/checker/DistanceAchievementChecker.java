package com.tomassirio.wanderer.command.service.impl.checker;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.DistanceCalculationStrategy;
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks achievements based on the total distance walked during a trip.
 *
 * <p>Uses an <strong>incremental</strong> approach: the cumulative distance is cached on {@link
 * Trip#getCachedDistanceKm()} and only the last segment (between the two most recent valid
 * locations) is calculated via the distance API on each invocation. This reduces external API calls
 * from O(N) to O(1) per trip update.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistanceAchievementChecker implements TripAchievementChecker {

    private final TripUpdateRepository tripUpdateRepository;
    private final TripRepository tripRepository;
    private final DistanceCalculationStrategy distanceCalculationStrategy;

    @Override
    public List<AchievementType> getApplicableTypes() {
        return List.of(
                AchievementType.DISTANCE_100KM,
                AchievementType.DISTANCE_200KM,
                AchievementType.DISTANCE_500KM,
                AchievementType.DISTANCE_800KM,
                AchievementType.DISTANCE_1000KM,
                AchievementType.DISTANCE_1600KM,
                AchievementType.DISTANCE_2200KM);
    }

    @Override
    public double computeMetric(Trip trip) {
        List<TripUpdate> updates =
                tripUpdateRepository.findByTripIdOrderByTimestampAsc(trip.getId());

        List<LatLng> coordinates =
                updates.stream()
                        .filter(
                                update ->
                                        update.getLocation() != null
                                                && update.getLocation().getLat() != null
                                                && update.getLocation().getLon() != null)
                        .map(
                                update ->
                                        new LatLng(
                                                update.getLocation().getLat(),
                                                update.getLocation().getLon()))
                        .toList();

        if (coordinates.size() < 2) {
            return 0.0;
        }

        double cachedDistance =
                trip.getCachedDistanceKm() != null ? trip.getCachedDistanceKm() : 0.0;

        // Only calculate the last segment (between the two most recent valid locations)
        List<LatLng> lastSegment =
                List.of(coordinates.get(coordinates.size() - 2), coordinates.getLast());

        double segmentDistance = distanceCalculationStrategy.calculatePathDistance(lastSegment);
        double totalDistance = cachedDistance + segmentDistance;

        // Persist the updated cached distance
        trip.setCachedDistanceKm(totalDistance);
        tripRepository.save(trip);

        log.debug(
                "Incremental distance for trip {}: segment={} km, total={} km",
                trip.getId(),
                segmentDistance,
                totalDistance);

        return totalDistance;
    }
}
