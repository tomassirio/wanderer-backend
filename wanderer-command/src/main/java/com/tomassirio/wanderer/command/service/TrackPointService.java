package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.command.controller.request.TrackPointsRequest;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ingests recorded GPS track points and derives a trip's route line and distance from them. */
public interface TrackPointService {

    /**
     * Stores a batch of track points for a trip the user owns. Points whose id is already known are
     * ignored. Accepted while the trip is IN_PROGRESS, RESTING or PAUSED; for a FINISHED trip only
     * points recorded up to the finish time are kept.
     *
     * @return the number of newly stored points
     * @throws IllegalStateException if the trip does not accept track points in its status
     */
    int recordTrackPoints(UUID userId, UUID tripId, TrackPointsRequest request);

    /**
     * Recomputes the trip's distance and encoded polyline from all its track points, then
     * broadcasts {@code POLYLINE_UPDATED} and, when {@code newPoints} is not empty, {@code
     * TRACK_UPDATED}. Runs serialized per trip.
     */
    void recomputeTrack(UUID tripId, List<TrackPointDTO> newPoints);

    /**
     * Track distance up to the given instant.
     *
     * @return the distance in km, or {@code null} if the trip has no track points
     */
    Double distanceAt(UUID tripId, Instant at);
}
