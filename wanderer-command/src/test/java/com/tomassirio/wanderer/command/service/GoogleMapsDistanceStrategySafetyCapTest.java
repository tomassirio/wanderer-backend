package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.maps.GeoApiContext;
import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.service.impl.strategy.GoogleMapsDistanceStrategy;
import com.tomassirio.wanderer.command.service.impl.strategy.HaversineDistanceStrategy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the safety cap in {@link GoogleMapsDistanceStrategy}. */
class GoogleMapsDistanceStrategySafetyCapTest {

    private GoogleMapsDistanceStrategy strategy;

    @BeforeEach
    void setUp() {
        GeoApiContext dummyContext = new GeoApiContext.Builder().apiKey("dummy-key").build();
        strategy = new GoogleMapsDistanceStrategy(dummyContext, new HaversineDistanceStrategy());
    }

    @Test
    void calculatePathDistance_whenExceedingSafetyCap_shouldFallBackToHaversine() {
        int coordinateCount = GoogleMapsDistanceStrategy.MAX_API_CALLS_PER_REQUEST + 2;
        List<LatLng> coordinates = new ArrayList<>();
        for (int i = 0; i < coordinateCount; i++) {
            coordinates.add(new LatLng(40.0 + (i * 0.01), -74.0));
        }

        double distance = strategy.calculatePathDistance(coordinates);

        assertThat(distance).isGreaterThan(0);
    }

    @Test
    void calculatePathDistance_whenNull_shouldReturnZero() {
        assertThat(strategy.calculatePathDistance(null)).isEqualTo(0.0);
    }

    @Test
    void calculatePathDistance_whenSingleCoordinate_shouldReturnZero() {
        assertThat(strategy.calculatePathDistance(List.of(new LatLng(40.0, -74.0)))).isEqualTo(0.0);
    }
}