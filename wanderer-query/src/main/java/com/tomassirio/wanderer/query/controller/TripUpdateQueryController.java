package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import com.tomassirio.wanderer.commons.dto.TripUpdateDTO;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import com.tomassirio.wanderer.query.service.TripUpdateService;
import com.tomassirio.wanderer.query.service.helper.TripVisibilityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for trip update query operations. Handles trip update retrieval requests.
 *
 * @since 0.4.2
 */
@RestController
@RequestMapping(value = ApiConstants.TRIPS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Trip Update Queries", description = "Endpoints for retrieving trip update information")
public class TripUpdateQueryController {

    private final TripUpdateService tripUpdateService;
    private final TripVisibilityHelper tripVisibilityHelper;

    @GetMapping(ApiConstants.TRIP_UPDATE_BY_ID_ENDPOINT)
    @Operation(
            summary = "Get trip update by ID",
            description = "Retrieves a specific trip update by its ID")
    public ResponseEntity<TripUpdateDTO> getTripUpdate(
            @Parameter(hidden = true) @CurrentUserId(required = false) UUID requestingUserId,
            @PathVariable UUID id) {
        log.info("Received request to retrieve trip update: {}", id);

        TripUpdateDTO tripUpdate = tripUpdateService.getTripUpdate(id);
        tripVisibilityHelper.assertCanView(UUID.fromString(tripUpdate.tripId()), requestingUserId);

        log.info("Successfully retrieved trip update with ID: {}", tripUpdate.id());
        return ResponseEntity.ok(tripUpdate);
    }

    @GetMapping(ApiConstants.TRIP_UPDATES_ENDPOINT)
    @Operation(
            summary = "Get all trip updates for a trip",
            description =
                    "Retrieves trip updates for a specific trip with pagination and sorting. "
                            + "Defaults to most recent first (timestamp descending). "
                            + "Use query parameters: page, size, sort (e.g., sort=timestamp,desc)")
    public ResponseEntity<Page<TripUpdateDTO>> getTripUpdatesForTrip(
            @Parameter(hidden = true) @CurrentUserId(required = false) UUID requestingUserId,
            @PathVariable UUID tripId,
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info(
                "Received request to retrieve trip updates for trip: {}, page: {}, size: {}",
                tripId,
                pageable.getPageNumber(),
                pageable.getPageSize());

        tripVisibilityHelper.assertCanView(tripId, requestingUserId);
        Page<TripUpdateDTO> tripUpdates = tripUpdateService.getTripUpdatesForTrip(tripId, pageable);

        log.info(
                "Successfully retrieved {} trip updates for trip {} (page {} of {})",
                tripUpdates.getNumberOfElements(),
                tripId,
                tripUpdates.getNumber() + 1,
                tripUpdates.getTotalPages());
        return ResponseEntity.ok(tripUpdates);
    }

    @GetMapping(ApiConstants.TRIP_TRACK_POINTS_ENDPOINT)
    @Operation(
            summary = "Get recorded track points for a trip",
            description =
                    "Returns the trip's recorded GPS track ordered by recordedAt. With 'since'"
                            + " (ISO instant), only points recorded after it are returned; used to"
                            + " backfill the route after live TRACK_UPDATED events.")
    public ResponseEntity<List<TrackPointDTO>> getTrackPoints(
            @Parameter(hidden = true) @CurrentUserId(required = false) UUID requestingUserId,
            @PathVariable UUID tripId,
            @Parameter(description = "Only points recorded after this instant (exclusive)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant since) {
        log.info("Received request to retrieve track points for trip {} since {}", tripId, since);

        tripVisibilityHelper.assertCanView(tripId, requestingUserId);

        List<TrackPointDTO> points = tripUpdateService.getTrackPoints(tripId, since);

        log.info("Returning {} track points for trip {}", points.size(), tripId);
        return ResponseEntity.ok(points);
    }
}
