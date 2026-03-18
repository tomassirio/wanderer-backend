package com.tomassirio.wanderer.command.service.impl.strategy;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixRow;
import com.google.maps.model.LatLng;
import com.google.maps.model.TravelMode;
import com.tomassirio.wanderer.command.service.DistanceCalculationStrategy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates distances using the Google Maps Distance Matrix API (walking mode).
 *
 * <p>Falls back to {@link HaversineDistanceStrategy} if an API call fails at runtime or if the
 * number of coordinate pairs exceeds {@link #MAX_API_CALLS_PER_REQUEST}.
 */
@Slf4j
@RequiredArgsConstructor
public class GoogleMapsDistanceStrategy implements DistanceCalculationStrategy {

    /**
     * Safety cap: maximum number of consecutive coordinate pairs that will be sent to the Google
     * Maps API in a single {@link #calculatePathDistance} call. If the list exceeds this limit, the
     * Haversine fallback is used instead to prevent runaway API costs.
     */
    public static final int MAX_API_CALLS_PER_REQUEST = 25;

    private final GeoApiContext geoApiContext;
    private final HaversineDistanceStrategy fallback;

    @Override
    public double calculatePathDistance(List<LatLng> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            return 0.0;
        }

        int segments = coordinates.size() - 1;
        if (segments > MAX_API_CALLS_PER_REQUEST) {
            log.warn(
                    "Coordinate list has {} segments, exceeding safety cap of {}. Falling back to"
                            + " Haversine to prevent excessive API costs.",
                    segments,
                    MAX_API_CALLS_PER_REQUEST);
            return fallback.calculatePathDistance(coordinates);
        }

        try {
            return calculateDistanceViaGoogleMaps(coordinates);
        } catch (Exception e) {
            log.warn(
                    "Failed to calculate distance via Google Maps API, falling back to"
                            + " Haversine: {}",
                    e.getMessage());
            return fallback.calculatePathDistance(coordinates);
        }
    }

    private double calculateDistanceViaGoogleMaps(List<LatLng> coordinates) throws Exception {
        double totalDistance = 0.0;

        for (int i = 0; i < coordinates.size() - 1; i++) {
            LatLng origin = coordinates.get(i);
            LatLng destination = coordinates.get(i + 1);

            DistanceMatrix matrix =
                    DistanceMatrixApi.newRequest(geoApiContext)
                            .origins(origin)
                            .destinations(destination)
                            .mode(TravelMode.WALKING)
                            .await();

            if (matrix != null && matrix.rows != null && matrix.rows.length > 0) {
                DistanceMatrixRow row = matrix.rows[0];
                if (row.elements != null && row.elements.length > 0) {
                    DistanceMatrixElement element = row.elements[0];
                    if (element.distance != null) {
                        totalDistance += element.distance.inMeters / 1000.0;
                    }
                }
            }
        }

        log.debug("Calculated distance via Google Maps API: {} km", totalDistance);
        return totalDistance;
    }
}
