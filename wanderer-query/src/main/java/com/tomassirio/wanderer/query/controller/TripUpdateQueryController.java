package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.TripUpdateDTO;
import com.tomassirio.wanderer.query.dto.TripUpdateLocationDTO;
import com.tomassirio.wanderer.query.service.TripUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping(ApiConstants.TRIP_UPDATE_BY_ID_ENDPOINT)
    @Operation(
            summary = "Get trip update by ID",
            description = "Retrieves a specific trip update by its ID")
    public ResponseEntity<TripUpdateDTO> getTripUpdate(@PathVariable UUID id) {
        log.info("Received request to retrieve trip update: {}", id);

        TripUpdateDTO tripUpdate = tripUpdateService.getTripUpdate(id);

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
            @PathVariable UUID tripId,
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info(
                "Received request to retrieve trip updates for trip: {}, page: {}, size: {}",
                tripId,
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<TripUpdateDTO> tripUpdates = tripUpdateService.getTripUpdatesForTrip(tripId, pageable);

        log.info(
                "Successfully retrieved {} trip updates for trip {} (page {} of {})",
                tripUpdates.getNumberOfElements(),
                tripId,
                tripUpdates.getNumber() + 1,
                tripUpdates.getTotalPages());
        return ResponseEntity.ok(tripUpdates);
    }

    @GetMapping(ApiConstants.TRIP_UPDATE_LOCATIONS_ENDPOINT)
    @Operation(
            summary = "Get all trip update locations for a map and timeline",
            description =
                    "Retrieves lightweight location data for all trip updates of a specific trip. "
                            + "Returns the fields needed for map marker rendering and timeline "
                            + "display (id, lat, lon, timestamp, updateType, battery, city, "
                            + "country, temperatureCelsius, weatherCondition). Not paginated "
                            + "because the map requires all points to render the complete route. "
                            + "Heavy fields (message, reactions) are excluded. "
                            + "Results are cached and ordered by timestamp ascending.")
    public ResponseEntity<List<TripUpdateLocationDTO>> getTripUpdateLocations(
            @PathVariable UUID tripId) {
        log.info("Received request to retrieve trip update locations for trip: {}", tripId);

        List<TripUpdateLocationDTO> locations = tripUpdateService.getTripUpdateLocations(tripId);

        log.info("Successfully retrieved {} locations for trip {}", locations.size(), tripId);
        return ResponseEntity.ok(locations);
    }
}
