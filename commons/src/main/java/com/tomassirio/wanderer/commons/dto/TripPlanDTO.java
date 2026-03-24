package com.tomassirio.wanderer.commons.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripPlanDTO(
        String id,
        String userId,
        String name,
        TripPlanType planType,
        LocalDate startDate,
        LocalDate endDate,
        GeoLocation startLocation,
        GeoLocation endLocation,
        List<GeoLocation> waypoints,
        String encodedPolyline,
        String plannedPolyline,
        Instant polylineUpdatedAt,
        Instant createdTimestamp) {

    @JsonProperty("thumbnailUrl")
    public String thumbnailUrl() {
        return id != null
                ? ThumbnailUrlService.generateTripPlanThumbnailUrl(UUID.fromString(id))
                : null;
    }
}
