package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripMaintenanceStatsDTO;
import com.tomassirio.wanderer.commons.mapper.TripMapper;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.TripService;
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
    private final UserRepository userRepository;

    private final TripMapper tripMapper = TripMapper.INSTANCE;

    @Override
    public TripDTO getTrip(UUID id) {
        Trip trip =
                tripRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        return enrichWithUsername(tripMapper.toDTO(trip));
    }

    @Override
    public Page<TripDTO> getAllTrips(Pageable pageable) {
        Page<Trip> tripPage = tripRepository.findAll(pageable);
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
        return enrichListWithUsernames(
                tripRepository.findByUserId(userId).stream().map(tripMapper::toDTO).toList());
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
    public Page<TripDTO> getOngoingPublicTrips(UUID requestingUserId, Pageable pageable) {
        if (requestingUserId == null) {
            Page<Trip> tripPage =
                    tripRepository.findByVisibilityAndStatusIn(
                            TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
            return enrichPageWithUsernames(tripPage, pageable);
        }

        // Get followed user IDs
        Set<UUID> followedUserIds =
                userFollowRepository.findByFollowerId(requestingUserId).stream()
                        .map(UserFollow::getFollowedId)
                        .collect(Collectors.toSet());

        if (followedUserIds.isEmpty()) {
            Page<Trip> tripPage =
                    tripRepository.findByVisibilityAndStatusIn(
                            TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
            return enrichPageWithUsernames(tripPage, pageable);
        }

        // Use unsorted pageable for the custom query (has its own ORDER BY)
        Pageable unsortedPageable =
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Trip> tripPage =
                tripRepository.findPublicActiveTripsWithFollowedPriority(
                        TripVisibility.PUBLIC,
                        TripStatus.getActiveStatuses(),
                        followedUserIds,
                        unsortedPageable);
        return enrichPageWithUsernames(tripPage, pageable);
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
        return enrichPageWithUsernames(tripPage, pageable);
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

        // Collect all unique user IDs
        Set<UUID> userIds =
                trips.stream()
                        .map(TripDTO::userId)
                        .filter(userId -> userId != null)
                        .map(UUID::fromString)
                        .collect(Collectors.toSet());

        // Fetch all users in a single query
        Map<UUID, String> userIdToUsername =
                userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));

        // Enrich each trip DTO with username
        return trips.stream()
                .map(
                        trip ->
                                new TripDTO(
                                        trip.id(),
                                        trip.name(),
                                        trip.userId(),
                                        trip.userId() != null
                                                ? userIdToUsername.get(
                                                        UUID.fromString(trip.userId()))
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
                                        trip.enabled()))
                .toList();
    }

    /**
     * Enriches a single TripDTO with username.
     *
     * @param trip TripDTO to enrich
     * @return enriched TripDTO with username populated
     */
    private TripDTO enrichWithUsername(TripDTO trip) {
        if (trip.userId() == null) {
            return trip;
        }

        String username =
                userRepository
                        .findById(UUID.fromString(trip.userId()))
                        .map(User::getUsername)
                        .orElse(null);

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
                trip.enabled());
    }

    @Override
    public TripMaintenanceStatsDTO getTripMaintenanceStats() {
        List<Trip> trips = tripRepository.findAll();

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
