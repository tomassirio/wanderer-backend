package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TripTrackPointRepository extends JpaRepository<TripTrackPoint, UUID> {

    boolean existsByTripId(UUID tripId);

    List<TripTrackPoint> findByTripIdOrderByRecordedAtAsc(UUID tripId);

    List<TripTrackPoint> findByTripIdAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
            UUID tripId, Instant recordedAt);

    @Query("SELECT p.id FROM TripTrackPoint p WHERE p.id IN :ids")
    Set<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    /** Inserts a point, silently skipping it when its id already exists. Returns rows inserted. */
    @Modifying
    @Query(
            value =
                    "INSERT INTO trip_track_points"
                            + " (id, trip_id, lat, lon, accuracy_m, altitude_m, recorded_at,"
                            + " received_at)"
                            + " VALUES (:#{#p.id}, :#{#p.tripId}, :#{#p.lat}, :#{#p.lon},"
                            + " :#{#p.accuracyM}, :#{#p.altitudeM}, :#{#p.recordedAt},"
                            + " :#{#p.receivedAt})"
                            + " ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int insertIgnoringDuplicate(@Param("p") TripTrackPoint p);
}
