package com.tomassirio.wanderer.commons.dto;

import com.tomassirio.wanderer.commons.domain.GeoLocation;
import java.time.Instant;
import java.util.List;

public record TripDetailsDTO(
        Instant startTimestamp,
        Instant endTimestamp,
        GeoLocation startLocation,
        GeoLocation endLocation,
        List<GeoLocation> waypoints,
        Integer currentDay) {}
