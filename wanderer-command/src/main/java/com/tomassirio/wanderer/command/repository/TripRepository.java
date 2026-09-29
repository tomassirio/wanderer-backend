package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.Trip;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findAllByUserId(UUID userId);

    @Query("SELECT DISTINCT u.trip.id FROM TripUpdate u")
    List<UUID> findIdsWithUpdates();
}
