package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.TripUpdate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TripUpdateRepository extends JpaRepository<TripUpdate, UUID> {

    List<TripUpdate> findByTripIdOrderByTimestampDesc(UUID tripId);

    List<TripUpdate> findByTripIdOrderByTimestampAsc(UUID tripId);

    Page<TripUpdate> findByTripId(UUID tripId, Pageable pageable);
    
    /**
     * Batch fetch trip updates for multiple trips to prevent N+1 queries
     */
    @Query("SELECT tu FROM TripUpdate tu WHERE tu.trip.id IN :tripIds ORDER BY tu.timestamp DESC")
    List<TripUpdate> findByTripIdIn(@Param("tripIds") List<UUID> tripIds);
}
