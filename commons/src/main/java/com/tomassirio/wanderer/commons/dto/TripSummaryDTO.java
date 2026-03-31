package com.tomassirio.wanderer.commons.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import java.time.Instant;

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
        Instant countdownStartDate,
        Instant polylineUpdatedAt) { // For cache-busting

    @JsonProperty("thumbnailUrl")
    public String thumbnailUrl() {
        boolean hasUpdates = updateCount != null && updateCount > 0;
        Long timestamp = polylineUpdatedAt != null ? polylineUpdatedAt.getEpochSecond() : null;
        return ThumbnailUrlService.resolveTripThumbnailUrl(id, hasUpdates, tripPlanId, timestamp);
    }
}
