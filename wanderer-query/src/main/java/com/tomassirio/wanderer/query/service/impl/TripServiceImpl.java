package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.config.RedisCacheConfig;
import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.PromotedTrip;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripMaintenanceStatsDTO;
import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import com.tomassirio.wanderer.commons.mapper.TripDetailsMapper;
import com.tomassirio.wanderer.commons.mapper.TripMapper;
import com.tomassirio.wanderer.commons.mapper.TripSettingsMapper;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.PromotedTripRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.TripUpdateRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.TripService;
import com.tomassirio.wanderer.query.service.helper.TripEnrichmentHelper;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
    private final UserRepository userRepository;
    private final PromotedTripRepository promotedTripRepository;
    private final TripEnrichmentHelper tripEnrichmentHelper;
    private final TripUpdateRepository tripUpdateRepository;

    private final TripMapper tripMapper = TripMapper.INSTANCE;

    @Override
    @Cacheable(value = RedisCacheConfig.TRIPS_CACHE, key = "#id", unless = "#result == null")
    public TripDTO getTrip(UUID id) {
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        return enrichWithUsernameAndPromotedStatus(tripMapper.toDTO(trip));
    }

    @Override
    public Page<TripDTO> getAllTrips(Pageable pageable) {
        Page<Trip> tripPage = tripRepository.findAll(pageable);
        
        // Batch fetch tripUpdates to avoid N+1 queries and populate them into trips
        List<UUID> tripIds = tripPage.getContent().stream()
                .map(Trip::getId)
                .collect(Collectors.toList());
        
        if (!tripIds.isEmpty()) {
            List<TripUpdate> allUpdates =
                    tripUpdateRepository.findByTripIdIn(tripIds);
            
            // Group updates by trip ID
            Map<UUID, List<TripUpdate>> updatesByTripId =
                    allUpdates.stream().collect(Collectors.groupingBy(
                            update -> update.getTrip().getId()));
            
            // Populate each trip's updates
            tripPage.getContent().forEach(trip -> {
                List<TripUpdate> tripUpdates =
                        updatesByTripId.getOrDefault(trip.getId(), List.of());
                trip.setTripUpdates(tripUpdates);
            });
        }
        
        return enrichPageWithUsernames(tripPage, pageable);
    }

    @Override
    public List<TripDTO> getPublicTrips() {
        return enrichListWithUsernames(
                tripRepository.findByTripSettingsVisibility(TripVisibility.PUBLIC).stream()
                        .map(tripMapper::toDTO)
                        .toList());
    }

    @Override
    public List<TripDTO> getTripsForUser(UUID userId) {
        List<TripDTO> trips = tripRepository.findByUserId(userId).stream()
                .map(tripMapper::toDTO)
                .toList();
        return enrichListWithUsernamesAndPromotedStatus(trips);
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

        return enrichListWithUsernames(
                tripRepository.findByUserIdAndVisibilityIn(userId, allowedVisibilities).stream()
                        .map(tripMapper::toDTO)
                        .toList());
    }

    @Override
    public Page<TripDTO> getTripsForUserWithVisibility(UUID userId, UUID requestingUserId, Pageable pageable) {
        // Check if users are friends
        boolean areFriends =
                requestingUserId != null
                        && friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId);

        // Determine allowed visibilities based on friendship status
        List<TripVisibility> allowedVisibilities =
                areFriends
                        ? List.of(TripVisibility.PUBLIC, TripVisibility.PROTECTED)
                        : List.of(TripVisibility.PUBLIC);

        Page<Trip> tripPage = tripRepository.findByUserIdAndVisibilityIn(userId, allowedVisibilities, pageable);
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
        List<TripDTO> enriched = enrichListWithUsernames(dtos);
        return new PageImpl<>(enriched, pageable, tripPage.getTotalElements());
    }

    /**
     * Enriches a list of TripDTOs with usernames by fetching users in batch.
     *
     * @param trips list of TripDTOs to enrich
     * @return list of enriched TripDTOs with usernames populated
     */
    private List<TripDTO> enrichListWithUsernames(List<TripDTO> trips) {
        if (trips.isEmpty()) {
            return trips;
        }

        Set<UUID> userIds = extractUserIdsFromDTOs(trips);
        Map<UUID, String> userIdToUsername =
                tripEnrichmentHelper.fetchUsernamesByUserIds(userIds);

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
    private List<TripDTO> enrichListWithUsernamesAndPromotedStatus(List<TripDTO> trips) {
        if (trips.isEmpty()) {
            return trips;
        }

        Set<UUID> tripIds = extractTripIdsFromDTOs(trips);
        Map<UUID, PromotedTrip> promotedTripsMap =
                tripEnrichmentHelper.fetchPromotedTripsMap(tripIds);

        Set<UUID> userIds = extractUserIdsFromDTOs(trips);
        Map<UUID, String> userIdToUsername =
                tripEnrichmentHelper.fetchUsernamesByUserIds(userIds);

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
                null);
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
                promotedInfo != null ? promotedInfo.getCountdownStartDate() : null);
    }

    /**
     * Enriches a single TripDTO with username and promoted status.
     *
     * @param trip the TripDTO to enrich
     * @return enriched TripDTO with username and promoted fields populated
     */
    private TripDTO enrichWithUsernameAndPromotedStatus(TripDTO trip) {
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
                promotedInfo != null ? promotedInfo.getCountdownStartDate() : null);
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
        if (tripPage.isEmpty()) {
            return Page.empty(originalPageable);
        }

        List<Trip> trips = tripPage.getContent();
        Map<UUID, PromotedTrip> promotedTripsMap =
                tripEnrichmentHelper.fetchAllPromotedTripsMap();
        Map<UUID, String> userIdToUsername = tripEnrichmentHelper.fetchUsernames(trips);

        List<TripDTO> enrichedTrips =
                trips.stream()
                        .map(
                                trip -> {
                                    PromotedTrip promotedInfo =
                                            promotedTripsMap.get(trip.getId());
                                    boolean isPromoted = promotedInfo != null;

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
                                            null, // comments
                                            null, // tripUpdates
                                            null, // tripDays
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
                                            promotedInfo != null
                                                    && promotedInfo.isPreAnnounced(),
                                            promotedInfo != null
                                                    ? promotedInfo.getCountdownStartDate()
                                                    : null);
                                })
                        .toList();

        return new PageImpl<>(enrichedTrips, originalPageable, tripPage.getTotalElements());
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
        if (tripPage.isEmpty()) {
            return Page.empty(originalPageable);
        }

        List<TripSummaryDTO> enrichedTrips =
                tripEnrichmentHelper.enrichTripsToSummaries(tripPage.getContent());

        return new PageImpl<>(enrichedTrips, originalPageable, tripPage.getTotalElements());
    }

    @Override
    public TripMaintenanceStatsDTO getTripMaintenanceStats() {
        List<Trip> trips = tripRepository.findAll();
        
        // Batch fetch trip updates to populate counts
        List<UUID> tripIds = trips.stream()
                .map(Trip::getId)
                .collect(Collectors.toList());
        
        if (!tripIds.isEmpty()) {
            List<TripUpdate> allUpdates = tripUpdateRepository.findByTripIdIn(tripIds);
            
            // Group updates by trip ID
            Map<UUID, List<TripUpdate>> updatesByTripId =
                    allUpdates.stream().collect(Collectors.groupingBy(
                            update -> update.getTrip().getId()));
            
            // Populate each trip's updates
            trips.forEach(trip -> {
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
