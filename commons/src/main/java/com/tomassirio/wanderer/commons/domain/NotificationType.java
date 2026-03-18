package com.tomassirio.wanderer.commons.domain;

/**
 * Enum representing the types of notifications that can be generated.
 *
 * @author tomassirio
 * @since 0.9.0
 */
public enum NotificationType {
    FRIEND_REQUEST_RECEIVED,
    FRIEND_REQUEST_ACCEPTED,
    FRIEND_REQUEST_DECLINED,
    COMMENT_ON_TRIP,
    REPLY_TO_COMMENT,
    COMMENT_REACTION,
    NEW_FOLLOWER,
    ACHIEVEMENT_UNLOCKED,
    TRIP_STATUS_CHANGED,
    TRIP_UPDATE_POSTED
}
