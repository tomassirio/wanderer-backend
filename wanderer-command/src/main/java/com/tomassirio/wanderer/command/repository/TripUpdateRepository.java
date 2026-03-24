package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.TripUpdate;
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
}
