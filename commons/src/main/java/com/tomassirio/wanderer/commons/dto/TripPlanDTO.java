package com.tomassirio.wanderer.commons.dto;

import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        Instant createdTimestamp) {}
