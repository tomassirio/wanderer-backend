package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.query.projection.TripSummary;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    
    /**
     * Find trip by ID with trip days eagerly loaded to prevent N+1 queries
     * Note: tripUpdates are fetched separately to avoid MultipleBagFetchException
     */
    @EntityGraph(attributePaths = {"tripDays"})
    Optional<Trip> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"tripDays"})
    List<Trip> findByTripSettingsVisibility(TripVisibility visibility);

    /**
     * Find trips by user ID with trip days eagerly loaded
     * Note: tripUpdates are fetched separately to avoid MultipleBagFetchException
     */
    @EntityGraph(attributePaths = {"tripDays"})
    List<Trip> findByUserId(UUID userId);

    /**
     * Find trips by user ID that are visible to the requester based on visibility rules. Returns
     * PUBLIC and PROTECTED trips for any requester. Excludes CREATED (draft) trips unless they are
     * pre-announced.
     */
    @Query(
            "SELECT t FROM Trip t WHERE t.userId = :userId AND t.tripSettings.visibility IN :visibilities "
                    + "AND (t.tripSettings.tripStatus != com.tomassirio.wanderer.commons.domain.TripStatus.CREATED OR "
                    + "EXISTS (SELECT 1 FROM PromotedTrip p WHERE p.tripId = t.id AND p.preAnnounced = true))")
    List<Trip> findByUserIdAndVisibilityIn(
            @Param("userId") UUID userId, @Param("visibilities") List<TripVisibility> visibilities);

    /** Find all public trips that are currently in progress. */
    @Query(
            "SELECT t FROM Trip t WHERE t.tripSettings.visibility = :visibility AND t.tripSettings.tripStatus = :status")
    List<Trip> findByVisibilityAndStatus(
            @Param("visibility") TripVisibility visibility, @Param("status") TripStatus status);

    /** Find all public trips that are in any of the specified statuses. */
    @Query(
            "SELECT t FROM Trip t WHERE t.tripSettings.visibility = :visibility AND t.tripSettings.tripStatus IN :statuses")
    List<Trip> findByVisibilityAndStatusIn(
            @Param("visibility") TripVisibility visibility,
            @Param("statuses") List<TripStatus> statuses);

    /** Find all public trips that are in any of the specified statuses (pageable with projection). */
    @Query(
            "SELECT t.id as id, t.name as name, t.userId as userId, " +
            "t.tripSettings.visibility as tripSettingsVisibility, " +
            "t.tripSettings.tripStatus as tripSettingsStatus, " +
            "t.tripSettings.tripModality as tripSettingsModality, " +
            "t.creationTimestamp as creationTimestamp, " +
            "t.cachedDistanceKm as cachedDistanceKm " +
            "FROM Trip t WHERE t.tripSettings.visibility = :visibility AND t.tripSettings.tripStatus IN :statuses")
    Page<TripSummary> findTripSummariesByVisibilityAndStatusIn(
            @Param("visibility") TripVisibility visibility,
            @Param("statuses") List<TripStatus> statuses,
            Pageable pageable);

    /** Find all public trips that are in any of the specified statuses (pageable). */
    @Query(
            "SELECT t FROM Trip t WHERE t.tripSettings.visibility = :visibility AND t.tripSettings.tripStatus IN :statuses")
    Page<Trip> findByVisibilityAndStatusIn(
            @Param("visibility") TripVisibility visibility,
            @Param("statuses") List<TripStatus> statuses,
            Pageable pageable);

    /**
     * Find all public trips in any of the specified statuses, prioritizing trips from followed
     * users. Followed users' trips are sorted first, then the rest, both ordered by creation
     * timestamp descending.
     */
    @Query(
            value =
                    "SELECT t FROM Trip t WHERE t.tripSettings.visibility = :visibility"
                            + " AND t.tripSettings.tripStatus IN :statuses"
                            + " ORDER BY CASE WHEN t.userId IN :followedUserIds THEN 0 ELSE 1 END,"
                            + " t.creationTimestamp DESC",
            countQuery =
                    "SELECT COUNT(t) FROM Trip t WHERE t.tripSettings.visibility = :visibility"
                            + " AND t.tripSettings.tripStatus IN :statuses")
    Page<Trip> findPublicActiveTripsWithFollowedPriority(
            @Param("visibility") TripVisibility visibility,
            @Param("statuses") List<TripStatus> statuses,
            @Param("followedUserIds") Set<UUID> followedUserIds,
            Pageable pageable);

    /**
     * Find all trips available to a user. This includes: - All trips owned by the user - All PUBLIC
     * trips from other users - All PROTECTED trips from friends
     */
    @Query(
            "SELECT t FROM Trip t WHERE "
                    + "t.userId = :userId OR "
                    + "t.tripSettings.visibility = 'PUBLIC' OR "
                    + "(t.tripSettings.visibility = 'PROTECTED' AND t.userId IN :friendIds)")
    List<Trip> findAllAvailableTripsForUser(
            @Param("userId") UUID userId, @Param("friendIds") List<UUID> friendIds);

    /**
     * Find all trips available to a user (pageable). This includes: - All trips owned by the user -
     * All PUBLIC trips from other users - All PROTECTED trips from friends
     */
    @Query(
            "SELECT t FROM Trip t WHERE "
                    + "t.userId = :userId OR "
                    + "t.tripSettings.visibility = 'PUBLIC' OR "
                    + "(t.tripSettings.visibility = 'PROTECTED' AND t.userId IN :friendIds)")
    Page<Trip> findAllAvailableTripsForUser(
            @Param("userId") UUID userId,
            @Param("friendIds") List<UUID> friendIds,
            Pageable pageable);

    long countByUserId(UUID userId);
}
