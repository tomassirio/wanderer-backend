package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.impl.checker.DistanceAchievementChecker;
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistanceAchievementCheckerTest {

    @Mock private TripRepository tripRepository;

    private DistanceAchievementChecker checker;

    private static final UUID TRIP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        checker = new DistanceAchievementChecker(tripRepository);
    }

    @Test
    void getApplicableTypes_shouldReturnAllDistanceAchievements() {
        List<AchievementType> types = checker.getApplicableTypes();

        assertThat(types)
                .containsExactly(
                        AchievementType.DISTANCE_100KM,
                        AchievementType.DISTANCE_200KM,
                        AchievementType.DISTANCE_500KM,
                        AchievementType.DISTANCE_800KM,
                        AchievementType.DISTANCE_1000KM,
                        AchievementType.DISTANCE_1600KM,
                        AchievementType.DISTANCE_2200KM);
    }

    @Test
    void computeMetric_whenNoUpdates_shouldReturnZero() {
        Trip trip = buildTrip(null);

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenCachedDistanceExists_shouldReturnCachedValue() {
        Trip trip = buildTrip(42.5);

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(42.5);
    }

    @Test
    void computeMetric_whenCachedDistanceIsZero_shouldReturnZero() {
        Trip trip = buildTrip(0.0);

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(0.0);
    }

    private Trip buildTrip(Double cachedDistance) {
        return Trip.builder()
                .id(TRIP_ID)
                .userId(UUID.randomUUID())
                .name("Test Trip")
                .cachedDistanceKm(cachedDistance)
                .creationTimestamp(Instant.now())
                .enabled(true)
                .build();
    }

    private TripUpdate buildUpdate(double lat, double lon) {
        return TripUpdate.builder()
                .id(UUID.randomUUID())
                .location(GeoLocation.builder().lat(lat).lon(lon).build())
                .timestamp(Instant.now())
                .build();
    }

    private TripUpdate buildUpdateWithNullLocation() {
        return TripUpdate.builder().id(UUID.randomUUID()).timestamp(Instant.now()).build();
    }
}
