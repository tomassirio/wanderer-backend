package com.tomassirio.wanderer.commons.dto;

import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Reactions;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
import java.time.Instant;

public record TripUpdateDTO(
        String id,
        String tripId,
        GeoLocation location,
        Integer battery,
        String message,
        Reactions reactions,
        String city,
        String country,
        Double temperatureCelsius,
        WeatherCondition weatherCondition,
        UpdateType updateType,
        Double distanceSoFarKm,
        Instant timestamp) {}
