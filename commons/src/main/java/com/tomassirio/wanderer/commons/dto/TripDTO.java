package com.tomassirio.wanderer.commons.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import java.time.Instant;
import java.util.List;

public record TripDTO(
        String id,
        String name,
        String userId,
        String username,
        TripSettingsDTO tripSettings,
        TripDetailsDTO tripDetails,
        String tripPlanId,
        List<CommentDTO> comments,
        List<TripUpdateDTO> tripUpdates,
        List<TripDayDTO> tripDays,
        String encodedPolyline,
        String plannedPolyline,
        Instant polylineUpdatedAt,
        Double accruedDistanceKm,
        Instant creationTimestamp,
        Boolean enabled,
        Boolean isPromoted,
        Instant promotedAt,
        Boolean isPreAnnounced,
        Instant countdownStartDate,
        Integer commentsCount,
        Integer updateCount) {

    @JsonProperty("thumbnailUrl")
    public String thumbnailUrl() {
        boolean hasUpdates = updateCount != null && updateCount > 0;
        return ThumbnailUrlService.resolveTripThumbnailUrl(id, hasUpdates, tripPlanId);
    }
}
