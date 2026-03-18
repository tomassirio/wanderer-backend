package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.repository.TripDayRepository;
import com.tomassirio.wanderer.command.service.impl.checker.DurationAchievementChecker;
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.Trip;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DurationAchievementCheckerTest {

    @Mock private TripDayRepository tripDayRepository;

    private DurationAchievementChecker checker;

    private static final UUID TRIP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        checker = new DurationAchievementChecker(tripDayRepository);
    }

    @Test
    void getApplicableTypes_shouldReturnAllDurationAchievements() {
        List<AchievementType> types = checker.getApplicableTypes();

        assertThat(types)
                .containsExactly(
                        AchievementType.DURATION_7_DAYS,
                        AchievementType.DURATION_30_DAYS,
                        AchievementType.DURATION_45_DAYS,
                        AchievementType.DURATION_60_DAYS);
    }

    @Test
    void computeMetric_whenTripIdIsNull_shouldReturnZero() {
        Trip trip = Trip.builder().build();

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenNoTripDays_shouldReturnZero() {
        Trip trip = Trip.builder().id(TRIP_ID).build();
        when(tripDayRepository.countByTripId(TRIP_ID)).thenReturn(0L);

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeMetric_whenTripHasDays_shouldReturnDayCount() {
        Trip trip = Trip.builder().id(TRIP_ID).build();
        when(tripDayRepository.countByTripId(TRIP_ID)).thenReturn(7L);

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(7.0);
    }

    @Test
    void computeMetric_whenTripHasManyDays_shouldReturnCorrectCount() {
        Trip trip = Trip.builder().id(TRIP_ID).build();
        when(tripDayRepository.countByTripId(TRIP_ID)).thenReturn(45L);

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(45.0);
    }
}

