package com.tomassirio.wanderer.command.websocket;

import java.util.UUID;

/**
 * Constants for WebSocket event types and topic patterns used throughout the application.
 *
 * <p>These constants are used when broadcasting events to WebSocket subscribers to ensure
 * consistent event naming and topic routing across the codebase.
 */
public final class WebSocketEventType {

    private WebSocketEventType() {
        // Prevent instantiation
    }

    // ==================== EVENT TYPES ====================

    // Trip lifecycle events
    public static final String TRIP_CREATED = "TRIP_CREATED";
    public static final String TRIP_DELETED = "TRIP_DELETED";
    public static final String TRIP_METADATA_UPDATED = "TRIP_METADATA_UPDATED";
    public static final String TRIP_STATUS_CHANGED = "TRIP_STATUS_CHANGED";
    public static final String TRIP_VISIBILITY_CHANGED = "TRIP_VISIBILITY_CHANGED";
    public static final String TRIP_UPDATED = "TRIP_UPDATED";
    public static final String TRIP_SETTINGS_UPDATED = "TRIP_SETTINGS_UPDATED";

    // Trip update side-effect events
    public static final String POLYLINE_UPDATED = "POLYLINE_UPDATED";

    // Trip Plan events
    public static final String TRIP_PLAN_CREATED = "TRIP_PLAN_CREATED";
    public static final String TRIP_PLAN_UPDATED = "TRIP_PLAN_UPDATED";
    public static final String TRIP_PLAN_DELETED = "TRIP_PLAN_DELETED";

    // Comment events
    public static final String COMMENT_ADDED = "COMMENT_ADDED";
    public static final String COMMENT_REACTION_ADDED = "COMMENT_REACTION_ADDED";
    public static final String COMMENT_REACTION_REMOVED = "COMMENT_REACTION_REMOVED";
    public static final String COMMENT_REACTION_REPLACED = "COMMENT_REACTION_REPLACED";

    // Friend request events
    public static final String FRIEND_REQUEST_SENT = "FRIEND_REQUEST_SENT";
    public static final String FRIEND_REQUEST_RECEIVED = "FRIEND_REQUEST_RECEIVED";
    public static final String FRIEND_REQUEST_ACCEPTED = "FRIEND_REQUEST_ACCEPTED";
    public static final String FRIEND_REQUEST_DECLINED = "FRIEND_REQUEST_DECLINED";
    public static final String FRIEND_REQUEST_CANCELLED = "FRIEND_REQUEST_CANCELLED";

    // User follow events
    public static final String USER_FOLLOWED = "USER_FOLLOWED";
    public static final String USER_UNFOLLOWED = "USER_UNFOLLOWED";

    // Achievement events
    public static final String ACHIEVEMENT_UNLOCKED = "ACHIEVEMENT_UNLOCKED";

    // User profile events
    public static final String USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    public static final String AVATAR_UPLOADED = "USER_AVATAR_UPLOADED";
    public static final String AVATAR_DELETED = "USER_AVATAR_DELETED";

    // ==================== TOPIC PATTERNS ====================

    private static final String TOPIC_TRIPS_PREFIX = "/topic/trips/";
    private static final String TOPIC_USERS_PREFIX = "/topic/users/";

    /**
     * Returns the WebSocket topic for trip-related events.
     *
     * @param tripId the trip ID
     * @return the topic string (e.g., "/topic/trips/{tripId}")
     */
    public static String tripTopic(UUID tripId) {
        return TOPIC_TRIPS_PREFIX + tripId;
    }

    /**
     * Returns the WebSocket topic for user-related events.
     *
     * @param userId the user ID
     * @return the topic string (e.g., "/topic/users/{userId}")
     */
    public static String userTopic(UUID userId) {
        return TOPIC_USERS_PREFIX + userId;
    }
}
