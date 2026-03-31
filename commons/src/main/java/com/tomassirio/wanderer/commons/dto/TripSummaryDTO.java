package com.tomassirio.wanderer.commons.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight DTO for trip summaries in list views (e.g., home feed). Contains only the essential
 * fields needed for displaying trip cards, significantly reducing payload size.
 *
 * @since 1.1.1
 */
public record TripSummaryDTO(
        String id,
        String name,
        String userId,
        String username,
        TripSettingsDTO tripSettings, // Contains status, visibility, tripModality
        Instant creationTimestamp,
        Integer commentsCount,
        Integer currentDay,
        String tripPlanId,
        Integer updateCount, // Number of trip updates/locations
        Boolean isPromoted,
        Instant promotedAt,
        Boolean isPreAnnounced,
        Instant countdownStartDate) {

    @JsonProperty("thumbnailUrl")
    public String thumbnailUrl() {
        if (id == null) {
            return null;
        }
        
        // If trip has no updates but has a trip plan, use the plan thumbnail
        boolean hasNoUpdates = updateCount == null || updateCount == 0;
        if (hasNoUpdates && tripPlanId != null && !tripPlanId.isEmpty()) {
            return ThumbnailUrlService.generateTripPlanThumbnailUrl(UUID.fromString(tripPlanId));
        }
        
        // Otherwise use trip thumbnail
        return ThumbnailUrlService.generateTripThumbnailUrl(UUID.fromString(id));
    }
}
