package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TripTrackPointRepository extends JpaRepository<TripTrackPoint, UUID> {

    @Query(
            "SELECT new com.tomassirio.wanderer.commons.dto.TrackPointDTO(p.lat, p.lon,"
                    + " p.recordedAt) FROM TripTrackPoint p WHERE p.tripId = :tripId"
                    + " ORDER BY p.recordedAt")
    List<TrackPointDTO> findTrack(@Param("tripId") UUID tripId);

    @Query(
            "SELECT new com.tomassirio.wanderer.commons.dto.TrackPointDTO(p.lat, p.lon,"
                    + " p.recordedAt) FROM TripTrackPoint p WHERE p.tripId = :tripId"
                    + " AND p.recordedAt > :since ORDER BY p.recordedAt")
    List<TrackPointDTO> findTrackSince(@Param("tripId") UUID tripId, @Param("since") Instant since);
}
