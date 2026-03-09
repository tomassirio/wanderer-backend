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
    public List<TripDTO> getAllTrips() {
        return enrichListWithUsernames(
                tripRepository.findAll().stream().map(tripMapper::toDTO).toList());
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
    public List<TripDTO> getOngoingPublicTrips(UUID requestingUserId) {
        List<Trip> publicTrips =
                tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, List.of(TripStatus.CREATED, TripStatus.IN_PROGRESS));

        if (requestingUserId == null) {
            return enrichListWithUsernames(publicTrips.stream().map(tripMapper::toDTO).toList());
        }

        // Get followed user IDs
        Set<UUID> followedUserIds =
                userFollowRepository.findByFollowerId(requestingUserId).stream()
                        .map(UserFollow::getFollowedId)
                        .collect(Collectors.toSet());

        Map<Boolean, List<Trip>> partitionedTrips =
                publicTrips.stream()
                        .collect(
                                Collectors.partitioningBy(
                                        trip -> followedUserIds.contains(trip.getUserId())));

        return enrichListWithUsernames(
                Stream.concat(
                                partitionedTrips.get(true).stream(),
                                partitionedTrips.get(false).stream())
                        .map(tripMapper::toDTO)
                        .toList());
    }

    @Override
    public List<TripDTO> getAllAvailableTripsForUser(UUID userId) {
        // Get all friend IDs
        List<UUID> friendIds =
                friendshipRepository.findByUserId(userId).stream()
                        .map(Friendship::getFriendId)
                        .toList();

        return enrichListWithUsernames(
                tripRepository.findAllAvailableTripsForUser(userId, friendIds).stream()
                        .map(tripMapper::toDTO)
                        .toList());
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
                                        trip.polylineUpdatedAt(),
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
                trip.polylineUpdatedAt(),
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
