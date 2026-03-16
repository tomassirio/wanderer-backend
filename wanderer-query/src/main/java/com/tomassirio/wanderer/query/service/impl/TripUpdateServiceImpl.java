package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.dto.TripUpdateDTO;
import com.tomassirio.wanderer.commons.mapper.TripUpdateMapper;
import com.tomassirio.wanderer.query.config.CacheConfig;
import com.tomassirio.wanderer.query.dto.TripUpdateLocationDTO;
import com.tomassirio.wanderer.query.repository.TripUpdateRepository;
import com.tomassirio.wanderer.query.service.TripUpdateService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
    private final TripUpdateMapper tripUpdateMapper = TripUpdateMapper.INSTANCE;

    @Override
    public TripUpdateDTO getTripUpdate(UUID id) {
        return tripUpdateRepository
                .findById(id)
                .map(tripUpdateMapper::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Trip update not found"));
    }

    @Override
    @Cacheable(
            value = CacheConfig.TRIP_UPDATES_CACHE,
            key =
                    "#tripId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    public Page<TripUpdateDTO> getTripUpdatesForTrip(UUID tripId, Pageable pageable) {
        return tripUpdateRepository.findByTripId(tripId, pageable).map(tripUpdateMapper::toDTO);
    }

    @Override
    @Cacheable(value = CacheConfig.TRIP_UPDATE_LOCATIONS_CACHE, key = "#tripId")
    public List<TripUpdateLocationDTO> getTripUpdateLocations(UUID tripId) {
        return tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId).stream()
                .map(
                        update ->
                                new TripUpdateLocationDTO(
                                        update.getId().toString(),
                                        update.getLocation() != null
                                                ? update.getLocation().getLat()
                                                : null,
                                        update.getLocation() != null
                                                ? update.getLocation().getLon()
                                                : null,
                                        update.getTimestamp(),
                                        update.getUpdateType(),
                                        update.getBattery(),
                                        update.getCity(),
                                        update.getCountry(),
                                        update.getTemperatureCelsius(),
                                        update.getWeatherCondition()))
                .toList();
    }
}
