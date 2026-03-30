package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import com.tomassirio.wanderer.query.service.TripService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for trip query operations. Handles trip retrieval requests.
 *
 * @since 0.1.8
 */
@RestController
@RequestMapping(value = ApiConstants.TRIPS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Trip Queries", description = "Endpoints for retrieving trip information")
public class TripController {

    private final TripService tripService;

    @GetMapping(ApiConstants.TRIP_BY_ID_ENDPOINT)
    @Operation(summary = "Get trip by ID", description = "Retrieves a specific trip by its ID")
    public ResponseEntity<TripDTO> getTrip(@PathVariable UUID id) {
        log.info("Received request to retrieve trip: {}", id);

        TripDTO trip = tripService.getTrip(id);

        log.info("Successfully retrieved trip with ID: {}", trip.id());
        return ResponseEntity.ok(trip);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(
            summary = "Get all trips",
            description =
                    "Retrieves all trips with pagination and sorting (admin only). "
                            + "Use query parameters: page, size, sort (e.g., sort=creationTimestamp,desc)")
    public ResponseEntity<Page<TripDTO>> getAllTrips(
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(
                            size = 20,
                            sort = "creationTimestamp",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info(
                "Received request to retrieve all trips, page: {}, size: {}",
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<TripDTO> trips = tripService.getAllTrips(pageable);

        log.info(
                "Successfully retrieved {} trips (page {} of {})",
                trips.getNumberOfElements(),
                trips.getNumber() + 1,
                trips.getTotalPages());
        return ResponseEntity.ok(trips);
    }

    @GetMapping(ApiConstants.ME_SUFFIX)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get trips for current authenticated user",
            description = "Retrieves all trips belonging to the authenticated user")
    public ResponseEntity<List<TripDTO>> getMyTrips(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        log.info("Received request to retrieve trips for current user: {}", userId);

        List<TripDTO> trips = tripService.getTripsForUser(userId);

        log.info("Successfully retrieved {} trips for user {}", trips.size(), userId);
        return ResponseEntity.ok(trips);
    }

    @GetMapping(ApiConstants.TRIPS_AVAILABLE_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get all available trips for current user",
            description =
                    "Retrieves all trips available to the authenticated user with pagination. "
                            + "This includes: all trips owned by the user (regardless of visibility), "
                            + "all public trips from other users, "
                            + "and all protected trips from friends. "
                            + "Use query parameters: page, size, sort (e.g., sort=creationTimestamp,desc)")
    public ResponseEntity<Page<TripDTO>> getAllAvailableTrips(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(
                            size = 20,
                            sort = "creationTimestamp",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info(
                "Received request to retrieve all available trips for user: {}, page: {}, size: {}",
                userId,
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<TripDTO> trips = tripService.getAllAvailableTripsForUser(userId, pageable);

        log.info(
                "Successfully retrieved {} available trips for user {} (page {} of {})",
                trips.getNumberOfElements(),
                userId,
                trips.getNumber() + 1,
                trips.getTotalPages());
        return ResponseEntity.ok(trips);
    }

    @GetMapping(ApiConstants.TRIPS_BY_USER_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get trips by another user",
            description =
                    "Retrieves trips by another user, respecting visibility (PUBLIC and PROTECTED if friends)")
    public ResponseEntity<List<TripDTO>> getTripsByUser(
            @Parameter(hidden = true) @CurrentUserId UUID requestingUserId,
            @PathVariable UUID userId) {
        log.info(
                "Received request to retrieve trips for user {} from user {}",
                userId,
                requestingUserId);

        List<TripDTO> trips = tripService.getTripsForUserWithVisibility(userId, requestingUserId);

        log.info("Successfully retrieved {} trips for user {}", trips.size(), userId);
        return ResponseEntity.ok(trips);
    }

    @GetMapping(ApiConstants.TRIPS_PUBLIC_ENDPOINT)
    @Operation(
            summary = "Get ongoing public trips (lightweight)",
            description =
                    "Retrieves public trips that are currently in progress with pagination, "
                            + "prioritizing followed users if authenticated. Returns lightweight trip summaries "
                            + "optimized for list views. Use query parameters: page, size, sort (e.g., sort=creationTimestamp,desc)")
    public ResponseEntity<Page<TripSummaryDTO>> getOngoingPublicTripSummaries(
            @Parameter(hidden = true) @CurrentUserId(required = false) UUID requestingUserId,
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(
                            size = 20,
                            sort = "creationTimestamp",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info(
                "Received request to retrieve ongoing public trip summaries from user {}, page: {}, size: {}",
                requestingUserId,
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<TripSummaryDTO> trips = tripService.getOngoingPublicTripSummaries(requestingUserId, pageable);

        log.info(
                "Successfully retrieved {} ongoing public trip summaries (page {} of {})",
                trips.getNumberOfElements(),
                trips.getNumber() + 1,
                trips.getTotalPages());
        return ResponseEntity.ok(trips);
    }
}
