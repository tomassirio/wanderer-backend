package com.tomassirio.wanderer.query.projection;

import java.util.UUID;

/**
 * Projection for User entity containing only basic information.
 * Used for list views and references to reduce data transfer.
 */
public interface UserSummary {
    UUID getId();
    String getUsername();
    String getDisplayName();
    String getProfilePictureUrl();
}
