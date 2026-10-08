package com.tomassirio.wanderer.command.service.impl;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripTrackPointRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.PolylineService;
import com.tomassirio.wanderer.command.service.RouteService;
import com.tomassirio.wanderer.command.service.TrackPointService;
import com.tomassirio.wanderer.command.service.helper.PolylineCodec;
import com.tomassirio.wanderer.command.service.helper.PolylineComputer;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PolylineService} that computes encoded polylines for trips using Google
 * Directions API (walking mode).
 *
 * <p>Supports incremental segment appending for optimal performance when new trip updates are
 * added, and full recomputation when trip updates are deleted. Trips with recorded track points get
 * their route from the track instead (see {@link TrackPointService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolylineServiceImpl implements PolylineService {

    private final TripRepository tripRepository;
    private final TripUpdateRepository tripUpdateRepository;
    private final TripTrackPointRepository trackPointRepository;
    private final TrackPointService trackPointService;
    private final RouteService routeService;
    private final PolylineComputer polylineComputer;
    private final ApplicationEventPublisher eventPublisher;

    // PolylineUpdatedEvent also drives the trip thumbnail: if this throws, the update gets no
    // thumbnail refresh (the admin regenerate-missing backfill recovers it).
    @Override
    @Transactional
    public void appendSegment(UUID tripId) {
        if (trackPointRepository.existsByTripId(tripId)) {
            // Recorded track owns the route; no Directions calls for this trip.
            log.debug("Trip {} has track points, skipping check-in segment", tripId);
            return;
        }
        Trip trip =
                tripRepository
                        .findById(tripId)
                        .orElseThrow(
                                () -> new EntityNotFoundException("Trip not found: " + tripId));

        List<TripUpdate> updates = tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId);

        // Filter to updates with valid locations
        List<TripUpdate> validUpdates =
                updates.stream()
                        .filter(
                                u ->
                                        u.getLocation() != null
                                                && u.getLocation().getLat() != null
                                                && u.getLocation().getLon() != null)
                        .toList();

        if (validUpdates.size() < 2) {
            // Not enough valid locations to compute a polyline
            trip.setEncodedPolyline(null);
            trip.setPolylineUpdatedAt(null);
            tripRepository.save(trip);
            log.debug("Trip {} has fewer than 2 valid locations, polyline cleared", tripId);
            publishPolylineUpdatedEvent(tripId, null, false);
            return;
        }

        GeoLocation previousLast = validUpdates.get(validUpdates.size() - 2).getLocation();
        GeoLocation newLast = validUpdates.getLast().getLocation();

        if (trip.getEncodedPolyline() != null && !trip.getEncodedPolyline().isEmpty()) {
            // Incremental: decode existing, fetch new segment, append, re-encode
            List<LatLng> existingPoints = PolylineCodec.decode(trip.getEncodedPolyline());

            List<LatLng> newSegmentPoints = routeService.getRoutePoints(previousLast, newLast);

            if (!newSegmentPoints.isEmpty()) {
                // Skip first point to avoid duplicate with last point of existing polyline
                existingPoints.addAll(newSegmentPoints.subList(1, newSegmentPoints.size()));
            }

            String encoded = PolylineCodec.encode(existingPoints);
            trip.setEncodedPolyline(encoded);
            trip.setPolylineUpdatedAt(Instant.now());
            tripRepository.save(trip);

            log.info(
                    "Polyline incrementally updated for trip {}. Total points: {}",
                    tripId,
                    existingPoints.size());
            publishPolylineUpdatedEvent(tripId, encoded, false);
        } else {
            // No existing polyline — full recompute
            recomputePolylineInternal(trip, updates, false);
        }
    }

    @Override
    @Transactional
    public void recomputePolyline(UUID tripId) {
        if (trackPointRepository.existsByTripId(tripId)) {
            trackPointService.recomputeTrack(tripId, List.of());
            return;
        }
        Trip trip =
                tripRepository
                        .findById(tripId)
                        .orElseThrow(
                                () -> new EntityNotFoundException("Trip not found: " + tripId));

        List<TripUpdate> updates = tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId);
        recomputePolylineInternal(trip, updates, true);
    }

    private void recomputePolylineInternal(
            Trip trip, List<TripUpdate> updates, boolean forceThumbnail) {
        List<GeoLocation> locations = updates.stream().map(TripUpdate::getLocation).toList();

        polylineComputer.computeAndApply(trip, locations, tripRepository::save);
        publishPolylineUpdatedEvent(trip.getId(), trip.getEncodedPolyline(), forceThumbnail);
    }

    private void publishPolylineUpdatedEvent(
            UUID tripId, String encodedPolyline, boolean forceThumbnail) {
        eventPublisher.publishEvent(
                PolylineUpdatedEvent.builder()
                        .tripId(tripId)
                        .encodedPolyline(encodedPolyline)
                        .forceThumbnail(forceThumbnail)
                        .build());
    }
}
