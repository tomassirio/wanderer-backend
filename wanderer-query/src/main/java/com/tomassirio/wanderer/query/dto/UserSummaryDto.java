package com.tomassirio.wanderer.query.dto;

import java.util.UUID;

/**
 * DTO projection for User entity containing only basic information.
 * Used for list views and references to reduce data transfer.
 */
public interface UserSummaryDto {
    UUID getId();
    String getUsername();
    String getDisplayName();
    String getProfilePictureUrl();
}
