package com.tomassirio.wanderer.commons.dto;

/**
 * Result of {@code POST /trips/{tripId}/track-points}.
 *
 * @param accepted how many points were newly stored (already-known ids are not counted)
 */
public record TrackPointsAcceptedResponse(int accepted) {}
