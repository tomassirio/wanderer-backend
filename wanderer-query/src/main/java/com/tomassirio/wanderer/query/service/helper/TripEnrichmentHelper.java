package com.tomassirio.wanderer.query.service.helper;

import com.tomassirio.wanderer.commons.domain.PromotedTrip;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import com.tomassirio.wanderer.commons.mapper.TripDetailsMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
                promotedInfo != null ? promotedInfo.getCountdownStartDate() : null,
                trip.getPolylineUpdatedAt()); // For cache-busting
    }

    /**
     * Enriches a page of Trip entities into TripDTOs with usernames, promoted status, and comment
     * counts. All lookups are done in batch to avoid N+1 queries.
     *
     * @param tripPage page of Trip entities
     * @param originalPageable the original pageable request
     * @return page of enriched TripDTOs
     */
    public Page<TripDTO> enrichTripsToTripDTOs(Page<Trip> tripPage, Pageable originalPageable) {
        if (tripPage.isEmpty()) {
            return Page.empty(originalPageable);
        }

        List<Trip> trips = tripPage.getContent();
        Set<UUID> tripIds = collectTripIds(trips);
        Map<UUID, PromotedTrip> promotedTripsMap = fetchAllPromotedTripsMap();
        Map<UUID, String> userIdToUsername = fetchUsernames(trips);
        Map<UUID, Long> commentCounts = fetchCommentCounts(tripIds);

        List<TripDTO> enrichedTrips =
                trips.stream()
                        .map(
                                trip -> {
                                    PromotedTrip promotedInfo =
                                            promotedTripsMap.get(trip.getId());
                                    boolean isPromoted = promotedInfo != null;
                                    Integer commentCount =
                                            trip.getId() != null
                                                    ? commentCounts
                                                            .getOrDefault(trip.getId(), 0L)
                                                            .intValue()
                                                    : 0;

                                    return new TripDTO(
                                            trip.getId() != null
                                                    ? trip.getId().toString()
                                                    : null,
                                            trip.getName(),
                                            trip.getUserId() != null
                                                    ? trip.getUserId().toString()
                                                    : null,
                                            trip.getUserId() != null
                                                    ? userIdToUsername.get(trip.getUserId())
                                                    : null,
                                            TripSettingsMapper.INSTANCE.toDTO(
                                                    trip.getTripSettings()),
                                            TripDetailsMapper.INSTANCE.toDTO(
                                                    trip.getTripDetails()),
                                            trip.getTripPlanId() != null
                                                    ? trip.getTripPlanId().toString()
                                                    : null,
                                            null,
                                            null,
                                            null,
                                            trip.getEncodedPolyline(),
                                            trip.getPlannedPolyline(),
                                            trip.getPolylineUpdatedAt(),
                                            trip.getCachedDistanceKm(),
                                            trip.getCreationTimestamp(),
                                            trip.getEnabled(),
                                            isPromoted,
                                            promotedInfo != null
                                                    ? promotedInfo.getPromotedAt()
                                                    : null,
                                            promotedInfo != null && promotedInfo.isPreAnnounced(),
                                            promotedInfo != null
                                                    ? promotedInfo.getCountdownStartDate()
                                                    : null,
                                            commentCount,
                                            trip.getUpdateCount());
                                })
                        .toList();

        return new PageImpl<>(enrichedTrips, originalPageable, tripPage.getTotalElements());
    }

    /**
     * Enriches a page of Trip entities into TripSummaryDTOs with usernames, promoted status, and
     * comment counts.
     *
     * @param tripPage page of Trip entities
     * @param originalPageable the original pageable request
     * @return page of enriched TripSummaryDTOs
     */
    public Page<TripSummaryDTO> enrichTripsToSummaryPage(
            Page<Trip> tripPage, Pageable originalPageable) {
        if (tripPage.isEmpty()) {
            return Page.empty(originalPageable);
        }

        List<TripSummaryDTO> enrichedTrips = enrichTripsToSummaries(tripPage.getContent());
        return new PageImpl<>(enrichedTrips, originalPageable, tripPage.getTotalElements());
    }

    /**
     * Enriches a list of TripDTOs with usernames by fetching users in batch.
     *
     * @param trips list of TripDTOs to enrich
     * @return list of enriched TripDTOs with usernames populated
     */
    public List<TripDTO> enrichListWithUsernames(List<TripDTO> trips) {
        if (trips.isEmpty()) {
            return trips;
        }

        Set<UUID> userIds = extractUserIdsFromDTOs(trips);
        Map<UUID, String> userIdToUsername = fetchUsernamesByUserIds(userIds);

        return trips.stream()
                .map(trip -> enrichTripDTOWithUsername(trip, userIdToUsername))
                .toList();
    }

    /**
     * Enriches a list of TripDTOs with usernames and promoted status by fetching data in batch.
     *
     * @param trips list of TripDTOs to enrich
     * @return list of enriched TripDTOs with usernames and promoted fields populated
     */
    public List<TripDTO> enrichListWithUsernamesAndPromotedStatus(List<TripDTO> trips) {
        if (trips.isEmpty()) {
            return trips;
        }

        Set<UUID> tripIds = extractTripIdsFromDTOs(trips);
        Map<UUID, PromotedTrip> promotedTripsMap = fetchPromotedTripsMap(tripIds);

        Set<UUID> userIds = extractUserIdsFromDTOs(trips);
        Map<UUID, String> userIdToUsername = fetchUsernamesByUserIds(userIds);

        return trips.stream()
                .map(
                        trip -> {
                            PromotedTrip promotedInfo =
                                    trip.id() != null
                                            ? promotedTripsMap.get(UUID.fromString(trip.id()))
                                            : null;
                            return enrichTripDTOWithUsernameAndPromoted(
                                    trip, userIdToUsername, promotedInfo);
                        })
                .toList();
    }

    /**
     * Enriches a single TripDTO with username and promoted status.
     *
     * @param trip the TripDTO to enrich
     * @return enriched TripDTO with username and promoted fields populated
     */
    public TripDTO enrichWithUsernameAndPromotedStatus(TripDTO trip) {
        if (trip.id() == null) {
            return trip;
        }

        UUID tripId = UUID.fromString(trip.id());
        
        // Get username
        String username = null;
        if (trip.userId() != null) {
            username = userRepository
                    .findById(UUID.fromString(trip.userId()))
                    .map(User::getUsername)
                    .orElse(null);
        }

        // Get promoted status
        PromotedTrip promotedInfo = promotedTripRepository.findByTripId(tripId).orElse(null);
        boolean isPromoted = promotedInfo != null;

        // Determine if the trip should be marked as pre-announced
        boolean isPreAnnounced = promotedInfo != null && promotedInfo.isPreAnnounced();
        
        return new TripDTO(
                trip.id(),
                trip.name(),
                trip.userId(),
                username,
                trip.tripSettings(),
                trip.tripDetails(),
                trip.tripPlanId(),
                trip.comments(),
                trip.tripUpdates(),
                trip.tripDays(),
                trip.encodedPolyline(),
                trip.plannedPolyline(),
                trip.polylineUpdatedAt(),
                trip.accruedDistanceKm(),
                trip.creationTimestamp(),
                trip.enabled(),
                isPromoted,
                promotedInfo != null ? promotedInfo.getPromotedAt() : null,
                isPreAnnounced,
                promotedInfo != null ? promotedInfo.getCountdownStartDate() : null,
                trip.commentsCount(),
                trip.updateCount());
    }

    /**
     * Extracts unique user IDs from a list of TripDTOs.
     *
     * @param trips list of TripDTOs
     * @return set of user UUIDs
     */
    private Set<UUID> extractUserIdsFromDTOs(List<TripDTO> trips) {
        return trips.stream()
                .map(TripDTO::userId)
                .filter(Objects::nonNull)
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    /**
     * Extracts unique trip IDs from a list of TripDTOs.
     *
     * @param trips list of TripDTOs
     * @return set of trip UUIDs
     */
    private Set<UUID> extractTripIdsFromDTOs(List<TripDTO> trips) {
        return trips.stream()
                .map(TripDTO::id)
                .filter(Objects::nonNull)
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    /**
     * Enriches a TripDTO with a username from the lookup map.
     *
     * @param trip the TripDTO to enrich
     * @param userIdToUsername map of user ID to username
     * @return enriched TripDTO with username populated
     */
    private TripDTO enrichTripDTOWithUsername(TripDTO trip, Map<UUID, String> userIdToUsername) {
        return new TripDTO(
                trip.id(),
                trip.name(),
                trip.userId(),
                trip.userId() != null
                        ? userIdToUsername.get(UUID.fromString(trip.userId()))
                        : null,
                trip.tripSettings(),
                trip.tripDetails(),
                trip.tripPlanId(),
                trip.comments(),
                trip.tripUpdates(),
                trip.tripDays(),
                trip.encodedPolyline(),
                trip.plannedPolyline(),
                trip.polylineUpdatedAt(),
                trip.accruedDistanceKm(),
                trip.creationTimestamp(),
                trip.enabled(),
                null,
                null,
                null,
                null,
                trip.commentsCount(),
                trip.updateCount());
    }

    /**
     * Enriches a TripDTO with a username and promoted status.
     *
     * @param trip the TripDTO to enrich
     * @param userIdToUsername map of user ID to username
     * @param promotedInfo the PromotedTrip info (nullable)
     * @return enriched TripDTO with username and promoted fields populated
     */
    private TripDTO enrichTripDTOWithUsernameAndPromoted(
            TripDTO trip, Map<UUID, String> userIdToUsername, PromotedTrip promotedInfo) {
        boolean isPromoted = promotedInfo != null;
        return new TripDTO(
                trip.id(),
                trip.name(),
                trip.userId(),
                trip.userId() != null
                        ? userIdToUsername.get(UUID.fromString(trip.userId()))
                        : null,
                trip.tripSettings(),
                trip.tripDetails(),
                trip.tripPlanId(),
                trip.comments(),
                trip.tripUpdates(),
                trip.tripDays(),
                trip.encodedPolyline(),
                trip.plannedPolyline(),
                trip.polylineUpdatedAt(),
                trip.accruedDistanceKm(),
                trip.creationTimestamp(),
                trip.enabled(),
                isPromoted,
                promotedInfo != null ? promotedInfo.getPromotedAt() : null,
                promotedInfo != null && promotedInfo.isPreAnnounced(),
                promotedInfo != null ? promotedInfo.getCountdownStartDate() : null,
                trip.commentsCount(),
                trip.updateCount());
    }
}


