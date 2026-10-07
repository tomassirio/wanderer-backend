package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findAllByUserId(UUID userId);

    Optional<Trip> findByUserIdAndStartIdempotencyKey(UUID userId, String startIdempotencyKey);

    List<Trip> findByTripSettingsTripStatus(TripStatus tripStatus);

    @Query("SELECT DISTINCT u.trip.id FROM TripUpdate u")
    List<UUID> findIdsWithUpdates();

    /** Loads a trip with its updates initialised, so it can be used outside a transaction. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.tripUpdates WHERE t.id = :id")
    Optional<Trip> findByIdWithUpdates(@Param("id") UUID id);
}
