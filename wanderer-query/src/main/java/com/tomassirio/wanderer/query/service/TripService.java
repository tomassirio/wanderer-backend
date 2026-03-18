package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripMaintenanceStatsDTO;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for querying trip data in the query side of the CQRS architecture.
 *
 * <p>This service handles all read operations for trips. It provides methods to retrieve trip
 * information without modifying the underlying data.
 *
 * @author tomassirio
 * @since 0.1.0
 */
public interface TripService {

    /**
     * Retrieves a single trip by its unique identifier.
     *
     * @param id the UUID of the trip to retrieve
     * @return a {@link TripDTO} containing the trip data
     * @throws jakarta.persistence.EntityNotFoundException if no trip exists with the given ID
     */
    TripDTO getTrip(UUID id);

    /**
     * Retrieves all trips in the system with pagination and sorting support.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of {@link TripDTO} objects representing trips
     */
    Page<TripDTO> getAllTrips(Pageable pageable);

    /**
     * Retrieves all trips with PUBLIC visibility.
     *
     * <p>This method is intended for unauthenticated users or public browsing. It returns only the
     * trips that are marked as PUBLIC, ensuring that sensitive or private trip data is not exposed.
     *
     * @return a list of {@link TripDTO} objects representing all public trips, or an empty list if
     *     no public trips exist
     */
    List<TripDTO> getPublicTrips();

    /**
     * Retrieves all trips that belong to the given user.
     *
     * @param userId the UUID of the owner/user
     * @return a list of {@link TripDTO} objects representing trips owned by the user, or an empty
     *     list if none exist
     */
    List<TripDTO> getTripsForUser(UUID userId);

    /**
     * Retrieves trips by another user, respecting visibility rules. Returns PUBLIC trips and
     * PROTECTED trips if the requesting user is friends with the trip owner.
     *
     * @param userId the UUID of the user whose trips to retrieve
     * @param requestingUserId the UUID of the user making the request (optional)
     * @return a list of {@link TripDTO} objects representing visible trips owned by the user
     */
    List<TripDTO> getTripsForUserWithVisibility(UUID userId, UUID requestingUserId);

    /**
     * Retrieves all ongoing public trips with pagination. If a requesting user ID is provided,
     * trips from followed users are prioritized.
     *
     * @param requestingUserId the UUID of the user making the request (optional)
     * @param pageable pagination and sorting parameters
     * @return a page of {@link TripDTO} objects representing ongoing public trips
     */
    Page<TripDTO> getOngoingPublicTrips(UUID requestingUserId, Pageable pageable);

    /**
     * Retrieves all trips available to the current user with pagination and sorting support. This
     * includes: - All trips owned by the user (regardless of visibility) - All public trips from
     * other users - All protected trips from users who are friends with the requesting user
     *
     * @param userId the UUID of the user making the request
     * @param pageable pagination and sorting parameters
     * @return a page of {@link TripDTO} objects representing all available trips for the user
     */
    Page<TripDTO> getAllAvailableTripsForUser(UUID userId, Pageable pageable);

    /**
     * Returns maintenance statistics for all trips in the system, including polyline coverage and
     * geocoding coverage metrics.
     *
     * @return a {@link TripMaintenanceStatsDTO} containing the computed statistics
     */
    TripMaintenanceStatsDTO getTripMaintenanceStats();
}
