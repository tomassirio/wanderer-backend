package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.TripDay;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripDayRepository extends JpaRepository<TripDay, UUID> {

    /**
     * Finds the current (open) day for a trip – i.e. the TripDay whose endTimestamp is still null.
     *
     * @param tripId the trip ID
     * @return the open TripDay, if any
     */
    Optional<TripDay> findByTripIdAndEndTimestampIsNull(UUID tripId);
}
