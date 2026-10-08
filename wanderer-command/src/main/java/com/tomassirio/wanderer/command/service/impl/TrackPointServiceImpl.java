package com.tomassirio.wanderer.command.service.impl;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.controller.request.TrackPointsRequest;
import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.event.TrackPointsRecordedEvent;
import com.tomassirio.wanderer.command.event.TrackUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripTrackPointRepository;
import com.tomassirio.wanderer.command.service.TrackPointService;
import com.tomassirio.wanderer.command.service.helper.PolylineCodec;
import com.tomassirio.wanderer.command.service.helper.TrackGeometry;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackPointServiceImpl implements TrackPointService {

    private final TripRepository tripRepository;
    private final TripTrackPointRepository trackPointRepository;
    private final OwnershipValidator ownershipValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public int recordTrackPoints(UUID userId, UUID tripId, TrackPointsRequest request) {
        Trip trip =
                tripRepository
                        .findById(tripId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        ownershipValidator.validateOwnership(trip, userId, Trip::getUserId, Trip::getId, "trip");

        Predicate<TrackPointsRequest.Point> accepted = acceptedFor(trip);

        Set<UUID> known =
                new HashSet<>(
                        trackPointRepository.findExistingIds(
                                request.points().stream()
                                        .map(TrackPointsRequest.Point::id)
                                        .toList()));
        Instant receivedAt = Instant.now();
        List<TripTrackPoint> fresh =
                request.points().stream()
                        .filter(accepted)
                        .filter(p -> known.add(p.id())) // drops stored and in-batch duplicates
                        .sorted(Comparator.comparing(TrackPointsRequest.Point::recordedAt))
                        .map(
                                p ->
                                        TripTrackPoint.builder()
                                                .id(p.id())
                                                .tripId(tripId)
                                                .lat(p.lat())
                                                .lon(p.lon())
                                                .accuracyM(p.accuracyM())
                                                .altitudeM(p.altitudeM())
                                                .recordedAt(p.recordedAt())
                                                .receivedAt(receivedAt)
                                                .build())
                        .toList();

        if (!fresh.isEmpty()) {
            eventPublisher.publishEvent(
                    TrackPointsRecordedEvent.builder().tripId(tripId).points(fresh).build());
        }
        log.debug(
                "Trip {}: {} of {} track points accepted",
                tripId,
                fresh.size(),
                request.points().size());
        return fresh.size();
    }

    private static Predicate<TrackPointsRequest.Point> acceptedFor(Trip trip) {
        TripStatus status =
                trip.getTripSettings() != null ? trip.getTripSettings().getTripStatus() : null;
        if (status == TripStatus.IN_PROGRESS
                || status == TripStatus.RESTING
                || status == TripStatus.PAUSED) {
            return p -> true;
        }
        if (status == TripStatus.FINISHED) {
            // Final flush after finishing: only what was recorded while the trip was still on.
            Instant end =
                    trip.getTripDetails() != null ? trip.getTripDetails().getEndTimestamp() : null;
            return p -> end != null && !p.recordedAt().isAfter(end);
        }
        throw new IllegalStateException(
                "Track points are not accepted for a trip in status " + status);
    }

    @Override
    @Transactional
    public void recomputeTrack(UUID tripId, List<TrackPointDTO> newPoints) {
        // ponytail: the trip row lock serializes recomputes per trip, but a waiting run parks an
        // async worker; move to a per-trip queue if uploads for one trip ever pile up.
        Trip trip =
                tripRepository
                        .findByIdForUpdate(tripId)
                        .orElseThrow(
                                () -> new EntityNotFoundException("Trip not found: " + tripId));

        // ponytail: full recompute per batch, O(points); append from the last processed point if
        // tracks get long enough for this to matter.
        List<LatLng> kept =
                TrackGeometry.filterJitter(
                        trackPointRepository.findByTripIdOrderByRecordedAtAsc(tripId));
        double distanceKm = TrackGeometry.distanceKm(kept);
        String encoded =
                kept.size() < 2
                        ? null
                        : PolylineCodec.encode(
                                TrackGeometry.simplify(kept, TrackGeometry.SIMPLIFY_TOLERANCE_M));

        trip.setCachedDistanceKm(distanceKm);
        trip.setEncodedPolyline(encoded);
        trip.setPolylineUpdatedAt(encoded != null ? Instant.now() : null);
        tripRepository.save(trip);

        eventPublisher.publishEvent(
                PolylineUpdatedEvent.builder().tripId(tripId).encodedPolyline(encoded).build());
        if (!newPoints.isEmpty()) {
            eventPublisher.publishEvent(
                    TrackUpdatedEvent.builder()
                            .tripId(tripId)
                            .points(newPoints)
                            .distanceKm(distanceKm)
                            .build());
        }
        log.info(
                "Track recomputed for trip {}: {} kept points, {} km",
                tripId,
                kept.size(),
                distanceKm);
    }

    @Override
    public Double distanceAt(UUID tripId, Instant at) {
        if (!trackPointRepository.existsByTripId(tripId)) {
            return null;
        }
        return TrackGeometry.distanceKm(
                TrackGeometry.filterJitter(
                        trackPointRepository
                                .findByTripIdAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
                                        tripId, at)));
    }
}
