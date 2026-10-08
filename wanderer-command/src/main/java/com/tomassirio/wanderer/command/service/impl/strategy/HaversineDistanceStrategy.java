package com.tomassirio.wanderer.command.service.impl.strategy;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.service.DistanceCalculationStrategy;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates distances between coordinates using the Haversine formula (great-circle distance).
 *
 * <p>This is the offline fallback strategy used when the Google Maps API is not configured or
 * disabled. It computes straight-line distances which will be shorter than actual walking/road
 * distances.
 */
@Slf4j
public class HaversineDistanceStrategy implements DistanceCalculationStrategy {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public double calculatePathDistance(List<LatLng> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;

        for (int i = 0; i < coordinates.size() - 1; i++) {
            LatLng current = coordinates.get(i);
            LatLng next = coordinates.get(i + 1);
            totalDistance += haversineKm(current.lat, current.lng, next.lat, next.lng);
        }

        log.debug("Calculated distance via Haversine formula: {} km", totalDistance);
        return totalDistance;
    }

    /** Great-circle distance in kilometers between two coordinates. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
