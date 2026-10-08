package com.tomassirio.wanderer.commons.dto;

import java.time.Instant;

/**
 * A recorded GPS fix as exposed to clients (route backfill and live {@code TRACK_UPDATED}).
 *
 * @param lat latitude
 * @param lon longitude
 * @param recordedAt when the phone captured the fix
 */
public record TrackPointDTO(Double lat, Double lon, Instant recordedAt) {}
