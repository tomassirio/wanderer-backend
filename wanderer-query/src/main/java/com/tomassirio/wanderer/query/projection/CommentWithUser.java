package com.tomassirio.wanderer.query.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection for Comment entity with user information.
 * Used to efficiently fetch comments without loading unnecessary trip data.
 */
public interface CommentWithUser {
    UUID getId();
    String getMessage();
    Instant getTimestamp();
    UUID getUserId();
    String getUserUsername();
    String getUserDisplayName();
    String getUserProfilePictureUrl();
}
