package com.tomassirio.wanderer.command.service.impl.checker;

import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.Trip;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks achievements based on the total distance walked during a trip.
 *
 * <p>The cumulative distance is maintained in {@link Trip#getCachedDistanceKm()} and updated
 * incrementally when new trip updates are added via {@link
 * com.tomassirio.wanderer.command.service.impl.TripUpdateServiceImpl}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistanceAchievementChecker implements TripAchievementChecker {

    private final TripRepository tripRepository;

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
        double cachedDistance =
                trip.getCachedDistanceKm() != null ? trip.getCachedDistanceKm() : 0.0;

        log.debug("Distance for trip {}: {} km", trip.getId(), cachedDistance);

        return cachedDistance;
    }
}
