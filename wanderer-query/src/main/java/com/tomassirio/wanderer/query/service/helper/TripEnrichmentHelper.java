package com.tomassirio.wanderer.query.service.helper;

import com.tomassirio.wanderer.commons.domain.PromotedTrip;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import com.tomassirio.wanderer.commons.mapper.TripSettingsMapper;
import com.tomassirio.wanderer.query.repository.CommentRepository;
import com.tomassirio.wanderer.query.repository.PromotedTripRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Helper component that encapsulates common trip enrichment logic shared between services. Handles
 * batch fetching of promoted trip data, usernames, and comment counts to avoid N+1 queries.
 *
 * @since 1.2.0
 */
@Component
@RequiredArgsConstructor
public class TripEnrichmentHelper {

    private final UserRepository userRepository;
    private final PromotedTripRepository promotedTripRepository;
    private final CommentRepository commentRepository;

    /**
     * Enriches a list of Trip entities into TripSummaryDTOs with usernames, promoted status, and
     * comment counts. All lookups are done in batch to avoid N+1 queries.
     *
     * @param trips the list of Trip entities to enrich
     * @return list of enriched TripSummaryDTOs
     */
    public List<TripSummaryDTO> enrichTripsToSummaries(List<Trip> trips) {
        if (trips.isEmpty()) {
            return List.of();
        }

        Set<UUID> tripIds = collectTripIds(trips);
        Map<UUID, PromotedTrip> promotedTripsMap = fetchPromotedTripsMap(tripIds);
        Map<UUID, String> userIdToUsername = fetchUsernames(trips);
        Map<UUID, Long> commentCounts = fetchCommentCounts(tripIds);

        return trips.stream()
                .map(
                        trip -> {
                            PromotedTrip promotedInfo = promotedTripsMap.get(trip.getId());
                            boolean isPromoted = promotedInfo != null;
                            return toTripSummaryDTO(
                                    userIdToUsername,
                                    commentCounts,
                                    trip,
                                    promotedInfo,
                                    isPromoted);
                        })
                .toList();
    }

    /**
     * Collects unique non-null trip IDs from a list of trips.
     *
     * @param trips the list of Trip entities
     * @return set of trip UUIDs
     */
    public Set<UUID> collectTripIds(List<Trip> trips) {
        return trips.stream()
                .map(Trip::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Fetches promoted trip details by trip IDs in a single query.
     *
     * @param tripIds the set of trip IDs to look up
     * @return map of trip ID to PromotedTrip
     */
    public Map<UUID, PromotedTrip> fetchPromotedTripsMap(Set<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        return promotedTripRepository.findByTripIdIn(tripIds).stream()
                .collect(Collectors.toMap(PromotedTrip::getTripId, pt -> pt));
    }

    /**
     * Fetches all promoted trip IDs and their details.
     *
     * @return map of trip ID to PromotedTrip for all promoted trips
     */
    public Map<UUID, PromotedTrip> fetchAllPromotedTripsMap() {
        Set<UUID> promotedTripIds = promotedTripRepository.findAllPromotedTripIds();
        return fetchPromotedTripsMap(promotedTripIds);
    }

    /**
     * Batch fetches usernames for all user IDs referenced in the given trips.
     *
     * @param trips the list of Trip entities
     * @return map of user ID to username
     */
    public Map<UUID, String> fetchUsernames(List<Trip> trips) {
        Set<UUID> userIds =
                trips.stream()
                        .map(Trip::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        return fetchUsernamesByUserIds(userIds);
    }

    /**
     * Batch fetches usernames for the given user IDs in a single query.
     *
     * @param userIds the set of user IDs to look up
     * @return map of user ID to username
     */
    public Map<UUID, String> fetchUsernamesByUserIds(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    /**
     * Batch fetches comment counts for the given trip IDs.
     *
     * @param tripIds the set of trip IDs
     * @return map of trip ID to comment count
     */
    public Map<UUID, Long> fetchCommentCounts(Set<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> tripIdsList = List.copyOf(tripIds);
        return commentRepository.countByTripIdIn(tripIdsList).stream()
                .collect(Collectors.toMap(arr -> (UUID) arr[0], arr -> (Long) arr[1]));
    }

    /**
     * Converts a Trip entity into a TripSummaryDTO with enriched data.
     *
     * @param userIdToUsername map of user ID to username
     * @param commentCounts map of trip ID to comment count
     * @param trip the Trip entity
     * @param promotedInfo the PromotedTrip info (nullable)
     * @param isPromoted whether the trip is promoted
     * @return a fully enriched TripSummaryDTO
     */
    public static TripSummaryDTO toTripSummaryDTO(
            Map<UUID, String> userIdToUsername,
            Map<UUID, Long> commentCounts,
            Trip trip,
            PromotedTrip promotedInfo,
            boolean isPromoted) {
        return new TripSummaryDTO(
                trip.getId() != null ? trip.getId().toString() : null,
                trip.getName(),
                trip.getUserId() != null ? trip.getUserId().toString() : null,
                trip.getUserId() != null ? userIdToUsername.get(trip.getUserId()) : null,
                TripSettingsMapper.INSTANCE.toDTO(trip.getTripSettings()),
                trip.getCreationTimestamp(),
                trip.getId() != null
                        ? commentCounts.getOrDefault(trip.getId(), 0L).intValue()
                        : 0,
                trip.getTripDetails() != null ? trip.getTripDetails().getCurrentDay() : null,
                trip.getTripPlanId() != null ? trip.getTripPlanId().toString() : null,
                trip.getUpdateCount(), // Include update count for thumbnail logic
                isPromoted,
                promotedInfo != null ? promotedInfo.getPromotedAt() : null,
                promotedInfo != null && promotedInfo.isPreAnnounced(),
                promotedInfo != null ? promotedInfo.getCountdownStartDate() : null);
    }
}

