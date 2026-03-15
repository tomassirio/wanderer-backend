package com.tomassirio.wanderer.command.controller;

import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripFromPlanRequest;
import com.tomassirio.wanderer.command.controller.request.TripSettingsRequest;
import com.tomassirio.wanderer.command.controller.request.TripStatusRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateRequest;
import com.tomassirio.wanderer.command.controller.request.TripVisibilityRequest;
import com.tomassirio.wanderer.command.service.TripDayService;
import com.tomassirio.wanderer.command.service.TripService;
import com.tomassirio.wanderer.command.service.TripUpdateService;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for trip command operations. Handles trip creation, update, and deletion
 * requests.
 *
 * @since 0.1.8
 */
@RestController
@RequestMapping(value = ApiConstants.TRIPS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Trips", description = "Endpoints for managing trips")
public class TripController {

    private final TripService tripService;
    private final TripDayService tripDayService;
    private final TripUpdateService tripUpdateService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Create a new trip",
            description =
                    "Creates a new trip with the provided details. Returns 202 Accepted with the trip ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> createTrip(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody TripCreationRequest request) {

        log.info("Received request to create trip by user {}", userId);

        UUID tripId = tripService.createTrip(userId, request);

        log.info("Accepted trip creation request with ID: {}", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tripId);
    }

    @PostMapping(
            value = ApiConstants.TRIP_FROM_PLAN_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Create a trip from a trip plan",
            description =
                    "Creates a new trip based on an existing trip plan. The trip will inherit the"
                            + " plan's name, start/end locations, and dates. The trip can be modified"
                            + " after creation. Returns 202 Accepted with the trip ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> createTripFromPlan(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Parameter(description = "ID of the trip plan to create a trip from") @PathVariable
                    UUID tripPlanId,
            @Valid @RequestBody TripFromPlanRequest request) {

        log.info("Received request to create trip from plan {} by user {}", tripPlanId, userId);

        UUID tripId = tripService.createTripFromPlan(userId, tripPlanId, request);

        log.info("Accepted trip creation request with ID: {} from plan", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tripId);
    }

    @PutMapping(
            value = ApiConstants.TRIP_BY_ID_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Update a trip",
            description =
                    "Updates an existing trip with new details. Returns 202 Accepted with the trip ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> updateTrip(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody TripUpdateRequest request) {
        log.info("Received request to update trip {} by user {}", id, userId);

        UUID tripId = tripService.updateTrip(userId, id, request);

        log.info("Accepted trip update request for ID: {}", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tripId);
    }

    @PatchMapping(
            value = ApiConstants.TRIP_VISIBILITY_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Change trip visibility",
            description =
                    "Updates the visibility setting of a trip (public/private). Returns 202 Accepted with the trip ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> changeVisibility(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody TripVisibilityRequest request) {
        log.info("Received request to change visibility for trip {} by user {}", id, userId);

        UUID tripId = tripService.changeVisibility(userId, id, request.visibility());

        log.info("Accepted visibility change request for trip ID: {}", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tripId);
    }

    @PatchMapping(
            value = ApiConstants.TRIP_STATUS_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Change trip status",
            description =
                    "Updates the status of a trip (planning/in_progress/completed/cancelled). Returns 202 Accepted with the trip ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> changeStatus(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody TripStatusRequest request) {
        log.info(
                "Received request to change status for trip {} to {} by user {}",
                id,
                request.status(),
                userId);

        UUID tripId = tripService.changeStatus(userId, id, request.status());

        log.info("Accepted status change request for trip ID: {}", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tripId);
    }

    @PatchMapping(ApiConstants.TRIP_SETTINGS_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Update trip settings",
            description =
                    "Updates trip settings including updateRefresh, automaticUpdates, and tripModality. Returns 202 Accepted with the trip ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> updateSettings(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody TripSettingsRequest request) {
        log.info("Received request to update settings for trip {} by user {}", id, userId);

        UUID tripId =
                tripService.updateSettings(
                        userId,
                        id,
                        request.updateRefresh(),
                        request.automaticUpdates(),
                        request.tripModality());

        log.info("Accepted settings update request for trip ID: {}", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tripId);
    }

    @DeleteMapping(ApiConstants.TRIP_BY_ID_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Delete a trip",
            description =
                    "Deletes a trip and all associated data. Returns 202 Accepted as the operation completes asynchronously.")
    public ResponseEntity<Void> deleteTrip(
            @Parameter(hidden = true) @CurrentUserId UUID userId, @PathVariable UUID id) {
        log.info("Received request to delete trip {} by user {}", id, userId);

        tripService.deleteTrip(userId, id);

        log.info("Accepted trip deletion request for ID: {}", id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping(
            value = ApiConstants.TRIP_UPDATES_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Create a trip update",
            description =
                    "Adds a new update to a trip with location, battery, and optional message. Returns 202 Accepted with the trip update ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> createTripUpdate(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripUpdateCreationRequest request) {
        log.info("Received request to create trip update for trip {} by user {}", tripId, userId);

        UUID updateId = tripUpdateService.createTripUpdate(userId, tripId, request);

        log.info("Accepted trip update creation request with ID: {}", updateId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(updateId);
    }

    @PatchMapping(ApiConstants.TRIP_TOGGLE_DAY_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Toggle day for a multi-day trip",
            description =
                    "Toggles the current day state of a multi-day trip. "
                            + "If the trip is IN_PROGRESS, the current day is finished and the trip "
                            + "status changes to RESTING. If the trip is RESTING, a new day starts "
                            + "and the trip status changes to IN_PROGRESS. "
                            + "Only available for trips with MULTI_DAY modality. "
                            + "Returns 202 Accepted with the trip ID.")
    public ResponseEntity<UUID> toggleDay(
            @Parameter(hidden = true) @CurrentUserId UUID userId, @PathVariable UUID id) {
        log.info("Received request to toggle day for trip {} by user {}", id, userId);

        UUID tripId = tripDayService.toggleDay(userId, id);

        log.info("Accepted toggle day request for trip ID: {}", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tripId);
    }
}
