package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailBackfillService;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.dto.ThumbnailBackfillResultDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailBackfillServiceImpl implements ThumbnailBackfillService {

    private final TripRepository tripRepository;
    private final ThumbnailService thumbnailService;

    @Override
    // read-only session keeps each trip's lazy updates loadable while its thumbnail is drawn
    @Transactional(readOnly = true)
    public ThumbnailBackfillResultDTO regenerateMissingTripThumbnails() {
        List<UUID> tripIds = tripRepository.findIdsWithUpdates();
        List<UUID> missing =
                tripIds.stream()
                        .filter(
                                id ->
                                        !thumbnailService.thumbnailExists(
                                                id, ThumbnailEntityType.TRIP))
                        .toList();

        int regenerated = 0;
        for (UUID tripId : missing) {
            if (regenerate(tripId)) {
                regenerated++;
            }
        }

        log.info(
                "Thumbnail backfill: checked={}, missing={}, regenerated={}, failed={}",
                tripIds.size(),
                missing.size(),
                regenerated,
                missing.size() - regenerated);
        return new ThumbnailBackfillResultDTO(
                tripIds.size(), missing.size(), regenerated, missing.size() - regenerated);
    }

    private boolean regenerate(UUID tripId) {
        try {
            Optional<Trip> trip = tripRepository.findById(tripId);
            if (trip.isEmpty()) {
                log.warn("Thumbnail backfill: trip {} no longer exists", tripId);
                return false;
            }
            thumbnailService.generateAndSaveThumbnail(trip.get());
        } catch (Exception e) {
            log.error("Thumbnail backfill: failed to generate thumbnail for trip {}", tripId, e);
            return false;
        }
        // generateAndSaveThumbnail logs and swallows its own IO errors, so the file is the truth
        boolean exists = thumbnailService.thumbnailExists(tripId, ThumbnailEntityType.TRIP);
        if (!exists) {
            log.error("Thumbnail backfill: thumbnail for trip {} still missing", tripId);
        }
        return exists;
    }
}
