package com.tomassirio.wanderer.commons.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        Instant promotedAt) {

    @JsonProperty("thumbnailUrl")
    public String thumbnailUrl() {
        return id != null
                ? ThumbnailUrlService.generateTripThumbnailUrl(UUID.fromString(id))
                : null;
    }
}
