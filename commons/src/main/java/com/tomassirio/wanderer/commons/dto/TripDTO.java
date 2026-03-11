package com.tomassirio.wanderer.commons.dto;

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
        Instant creationTimestamp,
        Boolean enabled) {}
