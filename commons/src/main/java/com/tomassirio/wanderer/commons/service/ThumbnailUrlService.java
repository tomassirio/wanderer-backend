package com.tomassirio.wanderer.commons.service;

import java.util.UUID;
import lombok.Getter;

public class ThumbnailUrlService {

    private static final String THUMBNAILS_BASE_PATH = "/thumbnails";

    @Getter
    public enum ThumbnailType {
        TRIP("trips"),
        TRIP_PLAN("plans"),
        USER_PROFILE("profiles");

        private final String path;

        ThumbnailType(String path) {
            this.path = path;
        }
    }

    public static String generateThumbnailUrl(UUID entityId, ThumbnailType type) {
        if (entityId == null) {
            return null;
        }
        return String.format("%s/%s/%s.png", THUMBNAILS_BASE_PATH, type.getPath(), entityId);
    }

    public static String generateTripThumbnailUrl(UUID tripId) {
        return generateThumbnailUrl(tripId, ThumbnailType.TRIP);
    }

    public static String generateTripPlanThumbnailUrl(UUID tripPlanId) {
        return generateThumbnailUrl(tripPlanId, ThumbnailType.TRIP_PLAN);
    }

    /**
     * Resolves the appropriate thumbnail URL for a trip. If the trip has no updates but has a trip
     * plan, falls back to the plan thumbnail. Otherwise uses the trip thumbnail. Includes a
     * cache-busting query parameter based on the polyline update timestamp.
     *
     * @param tripId the trip ID (as String)
     * @param hasUpdates whether the trip has any updates/locations
     * @param tripPlanId the trip plan ID (as String), may be null
     * @param polylineUpdatedAt the timestamp when polyline was last updated, used for cache busting
     * @return the resolved thumbnail URL with cache-busting parameter, or null if tripId is null
     */
    public static String resolveTripThumbnailUrl(
            String tripId, boolean hasUpdates, String tripPlanId, Long polylineUpdatedAt) {
        if (tripId == null) {
            return null;
        }
        String baseUrl;
        if (!hasUpdates && tripPlanId != null && !tripPlanId.isEmpty()) {
            baseUrl = generateTripPlanThumbnailUrl(UUID.fromString(tripPlanId));
        } else {
            baseUrl = generateTripThumbnailUrl(UUID.fromString(tripId));
        }

        // Add cache-busting parameter if polylineUpdatedAt is available
        if (polylineUpdatedAt != null && polylineUpdatedAt > 0) {
            return baseUrl + "?v=" + polylineUpdatedAt;
        }
        return baseUrl;
    }

    /**
     * Resolves the appropriate thumbnail URL for a trip without cache-busting. Kept for backward
     * compatibility.
     *
     * @param tripId the trip ID (as String)
     * @param hasUpdates whether the trip has any updates/locations
     * @param tripPlanId the trip plan ID (as String), may be null
     * @return the resolved thumbnail URL, or null if tripId is null
     * @deprecated Use {@link #resolveTripThumbnailUrl(String, boolean, String, Long)} instead
     */
    @Deprecated
    public static String resolveTripThumbnailUrl(
            String tripId, boolean hasUpdates, String tripPlanId) {
        return resolveTripThumbnailUrl(tripId, hasUpdates, tripPlanId, null);
    }

    public static String generateUserProfileThumbnailUrl(UUID userId) {
        return generateThumbnailUrl(userId, ThumbnailType.USER_PROFILE);
    }
}
