package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.PromotedTrip;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for querying PromotedTrip entities in the read model.
 *
 * <p>This repository is used by the query side of the CQRS architecture to retrieve information
 * about promoted trips.
 *
 * @author tomassirio
 * @since 0.5.0
 */
@Repository
public interface PromotedTripRepository extends JpaRepository<PromotedTrip, UUID> {

    /**
     * Finds a promoted trip by its trip ID.
     *
     * @param tripId the UUID of the trip
     * @return an Optional containing the PromotedTrip if found
     */
    Optional<PromotedTrip> findByTripId(UUID tripId);

    /**
     * Finds all trip IDs that are currently promoted.
     *
     * @return Set of promoted trip UUIDs
     */
    @Query("SELECT pt.tripId FROM PromotedTrip pt")
    Set<UUID> findAllPromotedTripIds();
}
