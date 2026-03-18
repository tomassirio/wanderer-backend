package com.tomassirio.wanderer.command.service.impl.checker;

import com.tomassirio.wanderer.command.repository.TripDayRepository;
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.Trip;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks achievements based on the number of trip days recorded for a trip.
 *
 * <p>Instead of computing elapsed calendar time between start and end timestamps, this checker
 * counts the actual {@link com.tomassirio.wanderer.commons.domain.TripDay} records associated with
 * the trip. This gives a more accurate representation of walking days, excluding rest days where no
 * TripDay was created.
 */
@Component
@RequiredArgsConstructor
public class DurationAchievementChecker implements TripAchievementChecker {

    private final TripDayRepository tripDayRepository;

    @Override
    public List<AchievementType> getApplicableTypes() {
        return List.of(
                AchievementType.DURATION_7_DAYS,
                AchievementType.DURATION_30_DAYS,
                AchievementType.DURATION_45_DAYS,
                AchievementType.DURATION_60_DAYS);
    }

    @Override
    public double computeMetric(Trip trip) {
        if (trip.getId() == null) {
            return 0.0;
        }

        return tripDayRepository.countByTripId(trip.getId());
    }
}
