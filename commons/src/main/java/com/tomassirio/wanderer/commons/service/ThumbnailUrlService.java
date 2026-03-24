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

    public static String generateUserProfileThumbnailUrl(UUID userId) {
        return generateThumbnailUrl(userId, ThumbnailType.USER_PROFILE);
    }
}
