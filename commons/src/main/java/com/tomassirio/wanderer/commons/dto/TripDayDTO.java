package com.tomassirio.wanderer.commons.dto;

import java.time.Instant;

public record TripDayDTO(
        String id,
        String tripId,
        Integer dayNumber,
        Instant startTimestamp,
        Instant endTimestamp) {}
