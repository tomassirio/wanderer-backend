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
        Boolean isPromoted,
        Instant promotedAt,
        Boolean isPreAnnounced,
        Instant countdownStartDate) {

    @JsonProperty("thumbnailUrl")
    public String thumbnailUrl() {
        return id != null
                ? ThumbnailUrlService.generateTripThumbnailUrl(UUID.fromString(id))
                : null;
    }
}
