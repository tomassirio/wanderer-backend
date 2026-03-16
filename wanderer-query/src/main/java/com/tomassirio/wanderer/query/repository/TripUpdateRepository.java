package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripUpdateRepository extends JpaRepository<TripUpdate, UUID> {

    List<TripUpdate> findByTripIdOrderByTimestampDesc(UUID tripId);

    List<TripUpdate> findByTripIdOrderByTimestampAsc(UUID tripId);

    Page<TripUpdate> findByTripId(UUID tripId, Pageable pageable);
}
