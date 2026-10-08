package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripUpdateRepository extends JpaRepository<TripUpdate, UUID> {
    long countByTripId(UUID tripId);

    List<TripUpdate> findByTripIdOrderByTimestampAsc(UUID tripId);

    Optional<TripUpdate> findFirstByTripIdAndLocationIsNotNullOrderByTimestampDesc(UUID tripId);

    /** The latest check-in of a trip, other than {@code id}, that already has a place name. */
    Optional<TripUpdate>
            findFirstByTripIdAndIdNotAndCityIsNotNullAndTimestampLessThanEqualOrderByTimestampDesc(
                    UUID tripId, UUID id, Instant timestamp);

    Optional<TripUpdate> findFirstByTripIdAndUpdateTypeOrderByTimestampAsc(
            UUID tripId, UpdateType updateType);
}
