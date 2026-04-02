package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripMaintenanceStatsDTO;
import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import com.tomassirio.wanderer.commons.mapper.TripMapper;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.TripUpdateRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.service.TripService;
import com.tomassirio.wanderer.query.service.helper.TripEnrichmentHelper;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserFollowRepository userFollowRepository;
    private final TripEnrichmentHelper tripEnrichmentHelper;
    private final TripUpdateRepository tripUpdateRepository;

    private final TripMapper tripMapper = TripMapper.INSTANCE;

    @Override
    public TripDTO getTrip(UUID id) {
        Trip trip =
                tripRepository
                        .findWithDetailsById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));

        trip.setTripUpdates(tripUpdateRepository.findByTripIdOrderByTimestampAsc(id));

        return tripEnrichmentHelper.enrichWithUsernameAndPromotedStatus(tripMapper.toDTO(trip));
    }

    @Override
    public Page<TripDTO> getAllTrips(Pageable pageable) {
        Page<Trip> tripPage = tripRepository.findAll(pageable);

        // Batch fetch tripUpdates to avoid N+1 queries and populate them into trips
        List<UUID> tripIds =
                tripPage.getContent().stream().map(Trip::getId).collect(Collectors.toList());

        if (!tripIds.isEmpty()) {
            List<TripUpdate> allUpdates = tripUpdateRepository.findByTripIdIn(tripIds);

            // Group updates by trip ID
            Map<UUID, List<TripUpdate>> updatesByTripId =
                    allUpdates.stream()
                            .collect(Collectors.groupingBy(update -> update.getTrip().getId()));

            // Populate each trip's updates
            tripPage.getContent()
                    .forEach(
                            trip -> {
                                List<TripUpdate> tripUpdates =
                                        updatesByTripId.getOrDefault(trip.getId(), List.of());
                                trip.setTripUpdates(tripUpdates);
                            });
        }

        return enrichPageWithUsernames(tripPage, pageable);
    }

    @Override
    public List<TripDTO> getPublicTrips() {
        return tripEnrichmentHelper.enrichListWithUsernames(
                tripRepository.findByTripSettingsVisibility(TripVisibility.PUBLIC).stream()
                        .map(tripMapper::toDTO)
                        .toList());
    }

    @Override
    public List<TripDTO> getTripsForUser(UUID userId) {
        List<TripDTO> trips =
                tripRepository.findByUserId(userId).stream().map(tripMapper::toDTO).toList();
        return tripEnrichmentHelper.enrichListWithUsernamesAndPromotedStatus(trips);
    }

    @Override
    public Page<TripDTO> getTripsForUser(UUID userId, Pageable pageable) {
        Page<Trip> tripPage = tripRepository.findByUserId(userId, pageable);
        return enrichPageWithUsernamesAndPromotedStatus(tripPage, pageable);
    }

    @Override
    public List<TripDTO> getTripsForUserWithVisibility(UUID userId, UUID requestingUserId) {
        // Check if users are friends
        boolean areFriends =
                requestingUserId != null
                        && friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId);

        // Determine allowed visibilities based on friendship status
        List<TripVisibility> allowedVisibilities =
                areFriends
                        ? List.of(TripVisibility.PUBLIC, TripVisibility.PROTECTED)
                        : List.of(TripVisibility.PUBLIC);

        return tripEnrichmentHelper.enrichListWithUsernames(
                tripRepository.findByUserIdAndVisibilityIn(userId, allowedVisibilities).stream()
                        .map(tripMapper::toDTO)
                        .toList());
    }

    @Override
    public Page<TripDTO> getTripsForUserWithVisibility(
            UUID userId, UUID requestingUserId, Pageable pageable) {
        // Check if users are friends
        boolean areFriends =
                requestingUserId != null
                        && friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId);

        // Determine allowed visibilities based on friendship status
        List<TripVisibility> allowedVisibilities =
                areFriends
                        ? List.of(TripVisibility.PUBLIC, TripVisibility.PROTECTED)
                        : List.of(TripVisibility.PUBLIC);

        Page<Trip> tripPage =
                tripRepository.findByUserIdAndVisibilityIn(userId, allowedVisibilities, pageable);
        return enrichPageWithUsernames(tripPage, pageable);
    }

    @Override
    public Page<TripDTO> getOngoingPublicTrips(UUID requestingUserId, Pageable pageable) {
        Page<Trip> tripPage;

        if (requestingUserId == null) {
            // Use optimized query that sorts promoted trips first
            tripPage =
                    tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                            TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
        } else {
            // Get followed user IDs
            Set<UUID> followedUserIds =
                    userFollowRepository.findByFollowerId(requestingUserId).stream()
                            .map(UserFollow::getFollowedId)
                            .collect(Collectors.toSet());

            if (followedUserIds.isEmpty()) {
                tripPage =
                        tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                                TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
            } else {
                // Use unsorted pageable for the custom query (has its own ORDER BY)
                Pageable unsortedPageable =
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
                tripPage =
                        tripRepository.findPublicActiveTripsWithFollowedPriority(
                                TripVisibility.PUBLIC,
                                TripStatus.getActiveStatuses(),
                                followedUserIds,
                                unsortedPageable);
            }
        }

        return enrichPageWithUsernamesAndPromotedStatus(tripPage, pageable);
    }

    @Override
    public Page<TripSummaryDTO> getOngoingPublicTripSummaries(
            UUID requestingUserId, Pageable pageable) {
        Page<Trip> tripPage;

        if (requestingUserId == null) {
            // Use optimized query that sorts promoted trips first
            tripPage =
                    tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                            TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
        } else {
            // Get followed user IDs
            Set<UUID> followedUserIds =
                    userFollowRepository.findByFollowerId(requestingUserId).stream()
                            .map(UserFollow::getFollowedId)
                            .collect(Collectors.toSet());

            if (followedUserIds.isEmpty()) {
                tripPage =
                        tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                                TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
            } else {
                // Use unsorted pageable for the custom query (has its own ORDER BY)
                Pageable unsortedPageable =
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
                tripPage =
                        tripRepository.findPublicActiveTripsWithFollowedPriority(
                                TripVisibility.PUBLIC,
                                TripStatus.getActiveStatuses(),
                                followedUserIds,
                                unsortedPageable);
            }
        }

        return enrichPageWithUsernamesAndPromotedStatusForSummaries(tripPage, pageable);
    }

    @Override
    public Page<TripDTO> getAllAvailableTripsForUser(UUID userId, Pageable pageable) {
        // Get all friend IDs
        List<UUID> friendIds =
                friendshipRepository.findByUserId(userId).stream()
                        .map(Friendship::getFriendId)
                        .toList();

        Page<Trip> tripPage =
                tripRepository.findAllAvailableTripsForUser(userId, friendIds, pageable);
        return enrichPageWithUsernamesAndPromotedStatus(tripPage, pageable);
    }

    /**
     * Enriches a Page of Trip entities with usernames, preserving pagination metadata.
     *
     * @param tripPage the page of Trip entities
     * @param pageable the original pageable request
     * @return a page of enriched TripDTOs with usernames populated
     */
    private Page<TripDTO> enrichPageWithUsernames(Page<Trip> tripPage, Pageable pageable) {
        List<TripDTO> dtos = tripPage.getContent().stream().map(tripMapper::toDTO).toList();
        List<TripDTO> enriched = tripEnrichmentHelper.enrichListWithUsernames(dtos);
        return new PageImpl<>(enriched, pageable, tripPage.getTotalElements());
    }

    /**
     * Enriches a page of trips with usernames and promoted status.
     *
     * @param tripPage page of Trip entities
     * @param originalPageable the original pageable request
     * @return page of enriched TripDTOs
     */
    private Page<TripDTO> enrichPageWithUsernamesAndPromotedStatus(
            Page<Trip> tripPage, Pageable originalPageable) {
        return tripEnrichmentHelper.enrichTripsToTripDTOs(tripPage, originalPageable);
    }

    /**
     * Enriches a page of trips with usernames and promoted status for summary DTOs (lightweight).
     *
     * @param tripPage page of Trip entities
     * @param originalPageable the original pageable request
     * @return page of enriched TripSummaryDTOs
     */
    private Page<TripSummaryDTO> enrichPageWithUsernamesAndPromotedStatusForSummaries(
            Page<Trip> tripPage, Pageable originalPageable) {
        return tripEnrichmentHelper.enrichTripsToSummaryPage(tripPage, originalPageable);
    }

    @Override
    public TripMaintenanceStatsDTO getTripMaintenanceStats() {
        List<Trip> trips = tripRepository.findAll();

        // Batch fetch trip updates to populate counts
        List<UUID> tripIds = trips.stream().map(Trip::getId).collect(Collectors.toList());

        if (!tripIds.isEmpty()) {
            List<TripUpdate> allUpdates = tripUpdateRepository.findByTripIdIn(tripIds);

            // Group updates by trip ID
            Map<UUID, List<TripUpdate>> updatesByTripId =
                    allUpdates.stream()
                            .collect(Collectors.groupingBy(update -> update.getTrip().getId()));

            // Populate each trip's updates
            trips.forEach(
                    trip -> {
                        List<TripUpdate> tripUpdates =
                                updatesByTripId.getOrDefault(trip.getId(), List.of());
                        trip.setTripUpdates(tripUpdates);
                        trip.setUpdateCount(tripUpdates.size()); // Update the count
                    });
        }

        long totalTrips = trips.size();
        long tripsWithPolyline =
                trips.stream()
                        .filter(
                                t ->
                                        t.getEncodedPolyline() != null
                                                && !t.getEncodedPolyline().isBlank())
                        .count();
        long tripsWithMultipleLocations =
                trips.stream()
                        .filter(t -> t.getTripUpdates() != null && t.getTripUpdates().size() >= 2)
                        .count();
        long tripsMissingPolyline =
                trips.stream()
                        .filter(
                                t ->
                                        t.getTripUpdates() != null
                                                && t.getTripUpdates().size() >= 2
                                                && (t.getEncodedPolyline() == null
                                                        || t.getEncodedPolyline().isBlank()))
                        .count();

        long totalUpdates =
                trips.stream()
                        .mapToLong(t -> t.getTripUpdates() != null ? t.getTripUpdates().size() : 0)
                        .sum();
        long updatesWithGeocoding =
                trips.stream()
                        .flatMap(
                                t ->
                                        t.getTripUpdates() != null
                                                ? t.getTripUpdates().stream()
                                                : Stream.empty())
                        .filter(
                                u ->
                                        u.getCity() != null
                                                && !u.getCity().isBlank()
                                                && u.getCountry() != null
                                                && !u.getCountry().isBlank())
                        .count();
        long updatesMissingGeocoding = totalUpdates - updatesWithGeocoding;

        return new TripMaintenanceStatsDTO(
                totalTrips,
                tripsWithPolyline,
                tripsWithMultipleLocations,
                tripsMissingPolyline,
                totalUpdates,
                updatesWithGeocoding,
                updatesMissingGeocoding);
    }
}
