package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import com.tomassirio.wanderer.commons.dto.TripUpdateDTO;
import com.tomassirio.wanderer.commons.mapper.TripUpdateMapper;
import com.tomassirio.wanderer.query.repository.TripTrackPointRepository;
import com.tomassirio.wanderer.query.repository.TripUpdateRepository;
import com.tomassirio.wanderer.query.service.TripUpdateService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link TripUpdateService} for querying trip update data.
 *
 * @since 0.4.2
 */
@Service
@AllArgsConstructor
public class TripUpdateServiceImpl implements TripUpdateService {

    private final TripUpdateRepository tripUpdateRepository;
    private final TripTrackPointRepository tripTrackPointRepository;
    private final TripUpdateMapper tripUpdateMapper = TripUpdateMapper.INSTANCE;

    @Override
    public TripUpdateDTO getTripUpdate(UUID id) {
        return tripUpdateRepository
                .findById(id)
                .map(tripUpdateMapper::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Trip update not found"));
    }

    @Override
    // TODO: Re-enable caching after fixing serialization issue with Pageable.sort (Sort object)
    // @Cacheable(
    //         value = RedisCacheConfig.TRIP_UPDATES_CACHE,
    //         key =
    //                 "#tripId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' +
    // #pageable.sort")
    public Page<TripUpdateDTO> getTripUpdatesForTrip(UUID tripId, Pageable pageable) {
        return tripUpdateRepository.findByTripId(tripId, pageable).map(tripUpdateMapper::toDTO);
    }

    @Override
    public List<TrackPointDTO> getTrackPoints(UUID tripId, Instant since) {
        return since == null
                ? tripTrackPointRepository.findTrack(tripId)
                : tripTrackPointRepository.findTrackSince(tripId, since);
    }
}
