package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistanceAchievementCheckerTest {

    @Mock private TripUpdateRepository tripUpdateRepository;

    @Mock private TripRepository tripRepository;

    @Mock private DistanceCalculationStrategy distanceCalculationStrategy;

    private DistanceAchievementChecker checker;

    private static final UUID TRIP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        checker =
                new DistanceAchievementChecker(
                        tripUpdateRepository, tripRepository, distanceCalculationStrategy);
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
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(TRIP_ID)).thenReturn(List.of());

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(0.0);
        verify(distanceCalculationStrategy, never()).calculatePathDistance(any());
        verify(tripRepository, never()).save(any());
    }

    @Test
    void computeMetric_whenOneValidUpdate_shouldReturnZero() {
        Trip trip = buildTrip(null);
        TripUpdate update = buildUpdate(42.0, -8.0);
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(TRIP_ID))
                .thenReturn(List.of(update));

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(0.0);
        verify(distanceCalculationStrategy, never()).calculatePathDistance(any());
    }

    @Test
    void computeMetric_whenTwoValidUpdates_shouldCalculateOnlyLastSegment() {
        Trip trip = buildTrip(null);
        TripUpdate u1 = buildUpdate(42.0, -8.0);
        TripUpdate u2 = buildUpdate(42.1, -8.1);
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(TRIP_ID))
                .thenReturn(List.of(u1, u2));
        when(distanceCalculationStrategy.calculatePathDistance(any())).thenReturn(12.5);

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(12.5);

        // Verify only 2 coordinates (the last segment) are passed
        ArgumentCaptor<List<LatLng>> captor = ArgumentCaptor.forClass(List.class);
        verify(distanceCalculationStrategy).calculatePathDistance(captor.capture());
        assertThat(captor.getValue()).hasSize(2);

        // Verify cached distance is persisted
        assertThat(trip.getCachedDistanceKm()).isEqualTo(12.5);
        verify(tripRepository).save(trip);
    }

    @Test
    void computeMetric_whenCachedDistanceExists_shouldAddIncrementally() {
        Trip trip = buildTrip(100.0);
        TripUpdate u1 = buildUpdate(42.0, -8.0);
        TripUpdate u2 = buildUpdate(42.1, -8.1);
        TripUpdate u3 = buildUpdate(42.2, -8.2);
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(TRIP_ID))
                .thenReturn(List.of(u1, u2, u3));
        when(distanceCalculationStrategy.calculatePathDistance(any())).thenReturn(15.0);

        double result = checker.computeMetric(trip);

        // 100 (cached) + 15 (new segment) = 115
        assertThat(result).isEqualTo(115.0);

        // Verify only the last segment (u2 → u3) was sent to the strategy
        ArgumentCaptor<List<LatLng>> captor = ArgumentCaptor.forClass(List.class);
        verify(distanceCalculationStrategy).calculatePathDistance(captor.capture());
        List<LatLng> segment = captor.getValue();
        assertThat(segment).hasSize(2);
        assertThat(segment.get(0).lat).isEqualTo(42.1);
        assertThat(segment.get(1).lat).isEqualTo(42.2);

        assertThat(trip.getCachedDistanceKm()).isEqualTo(115.0);
        verify(tripRepository).save(trip);
    }

    @Test
    void computeMetric_whenUpdatesHaveNullLocations_shouldFilterThem() {
        Trip trip = buildTrip(50.0);
        TripUpdate valid1 = buildUpdate(42.0, -8.0);
        TripUpdate nullLoc = buildUpdateWithNullLocation();
        TripUpdate valid2 = buildUpdate(42.1, -8.1);
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(TRIP_ID))
                .thenReturn(List.of(valid1, nullLoc, valid2));
        when(distanceCalculationStrategy.calculatePathDistance(any())).thenReturn(10.0);

        double result = checker.computeMetric(trip);

        // 50 (cached) + 10 (segment between valid1 and valid2) = 60
        assertThat(result).isEqualTo(60.0);

        ArgumentCaptor<List<LatLng>> captor = ArgumentCaptor.forClass(List.class);
        verify(distanceCalculationStrategy).calculatePathDistance(captor.capture());
        List<LatLng> segment = captor.getValue();
        assertThat(segment).hasSize(2);
        assertThat(segment.get(0).lat).isEqualTo(42.0);
        assertThat(segment.get(1).lat).isEqualTo(42.1);
    }

    @Test
    void computeMetric_whenAllLocationsNull_shouldReturnZero() {
        Trip trip = buildTrip(null);
        TripUpdate nullLoc1 = buildUpdateWithNullLocation();
        TripUpdate nullLoc2 = buildUpdateWithNullLocation();
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(TRIP_ID))
                .thenReturn(List.of(nullLoc1, nullLoc2));

        double result = checker.computeMetric(trip);

        assertThat(result).isEqualTo(0.0);
        verify(distanceCalculationStrategy, never()).calculatePathDistance(any());
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
