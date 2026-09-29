package com.tomassirio.wanderer.command.controller;

import com.tomassirio.wanderer.command.controller.request.PromoteTripRequest;
import com.tomassirio.wanderer.command.service.PolylineService;
import com.tomassirio.wanderer.command.service.PromotedTripService;
import com.tomassirio.wanderer.command.service.ThumbnailBackfillService;
import com.tomassirio.wanderer.command.service.TripService;
import com.tomassirio.wanderer.command.service.TripUpdateGeocodingService;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.ThumbnailBackfillResultDTO;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for admin trip operations.
 *
 * <p>All endpoints in this controller require ADMIN role. Includes trip maintenance (polyline
 * recomputation) and trip promotion management.
 *
 * @since 0.8.0
 */
@RestController
@RequestMapping(value = ApiConstants.ADMIN_TRIPS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Admin Trip Maintenance",
        description =
                "Admin-only endpoints for trip data maintenance"
                        + " (polyline, geocoding, promotion)")
public class AdminTripController {

    private final PolylineService polylineService;
    private final PromotedTripService promotedTripService;
    private final TripUpdateGeocodingService tripUpdateGeocodingService;
    private final ThumbnailBackfillService thumbnailBackfillService;
    private final TripService tripService;

    /**
     * Recomputes the encoded polyline for a trip from all its trip updates.
     *
     * <p>This is an admin-only maintenance operation, useful when the polyline is stale, corrupted,
     * or needs to be regenerated after a routing provider change.
     *
     * @param tripId the ID of the trip to recompute
     * @return 204 No Content on success
     */
    @PostMapping(ApiConstants.ADMIN_TRIP_RECOMPUTE_POLYLINE_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Recompute trip polyline",
            description =
                    "Fully recomputes the encoded polyline for a trip from all its "
                            + "location updates. Admin-only maintenance endpoint.")
    @ApiResponse(responseCode = "204", description = "Polyline recomputed successfully")
    @ApiResponse(
            responseCode = "404",
            description = "Trip not found",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - valid JWT required",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden - ADMIN role required",
            content = @Content)
    public ResponseEntity<Void> recomputePolyline(
            @Parameter(description = "Trip ID to recompute polyline for", required = true)
                    @PathVariable
                    UUID tripId) {
        log.info("Admin recomputing polyline for trip {}", tripId);
        polylineService.recomputePolyline(tripId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Generates the thumbnail of every trip with updates whose thumbnail file is missing.
     *
     * <p>Runs synchronously, one trip at a time (each generation calls Google Static Maps), and
     * only for trips without a file. A failure for one trip does not stop the rest.
     *
     * @return 200 OK with checked/missing/regenerated/failed counts
     */
    @PostMapping(ApiConstants.ADMIN_TRIP_THUMBNAILS_REGENERATE_MISSING_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Regenerate missing trip thumbnails",
            description =
                    "Generates thumbnails for all trips with updates that have no thumbnail file."
                            + " Admin-only maintenance endpoint.")
    @ApiResponse(responseCode = "200", description = "Backfill finished; summary returned")
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - valid JWT required",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden - ADMIN role required",
            content = @Content)
    public ResponseEntity<ThumbnailBackfillResultDTO> regenerateMissingThumbnails() {
        log.info("Admin regenerating missing trip thumbnails");
        return ResponseEntity.ok(thumbnailBackfillService.regenerateMissingTripThumbnails());
    }

    /**
     * Recomputes the reverse-geocoded city and country for every trip update belonging to a trip.
     *
     * <p>This is an admin-only maintenance operation, useful when geocoding data is missing or
     * needs to be refreshed (e.g. after a provider change or after backfilling existing records).
     *
     * @param tripId the ID of the trip whose updates should be re-geocoded
     * @return 204 No Content on success
     */
    @PostMapping(ApiConstants.ADMIN_TRIP_RECOMPUTE_GEOCODING_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Recompute trip update geocoding",
            description =
                    "Re-runs reverse geocoding on every trip update for the given trip, "
                            + "updating only city and country. Admin-only maintenance endpoint.")
    @ApiResponse(responseCode = "204", description = "Geocoding recomputed successfully")
    @ApiResponse(
            responseCode = "404",
            description = "Trip not found",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - valid JWT required",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden - ADMIN role required",
            content = @Content)
    public ResponseEntity<Void> recomputeGeocoding(
            @Parameter(description = "Trip ID to recompute geocoding for", required = true)
                    @PathVariable
                    UUID tripId) {
        log.info("Admin recomputing geocoding for trip {}", tripId);
        tripUpdateGeocodingService.recomputeGeocoding(tripId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Promotes a trip, optionally including a donation link.
     *
     * @param adminId the admin user performing the operation
     * @param tripId the ID of the trip to promote
     * @param request optional donation link
     * @return 202 Accepted with the promoted trip ID
     */
    @PostMapping(
            value = ApiConstants.ADMIN_TRIP_PROMOTE_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Promote a trip",
            description =
                    "Promotes a trip to be featured. Optionally includes a donation link. "
                            + "Returns 202 Accepted with the promoted trip ID.")
    @ApiResponse(responseCode = "202", description = "Trip promoted successfully")
    @ApiResponse(
            responseCode = "404",
            description = "Trip not found",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - valid JWT required",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden - ADMIN role required",
            content = @Content)
    public ResponseEntity<UUID> promoteTrip(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Parameter(description = "Trip ID to promote", required = true) @PathVariable
                    UUID tripId,
            @Valid @RequestBody PromoteTripRequest request) {
        log.info("Admin {} promoting trip {}", adminId, tripId);

        UUID promotedTripId =
                promotedTripService.promoteTrip(
                        adminId,
                        tripId,
                        request != null ? request.donationLink() : null,
                        Boolean.TRUE.equals(request != null ? request.isPreAnnounced() : null),
                        request != null ? request.countdownStartDate() : null);

        log.info("Accepted trip promotion request with ID: {}", promotedTripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(promotedTripId);
    }

    /**
     * Removes promotion from a trip.
     *
     * @param adminId the admin user performing the operation
     * @param tripId the ID of the trip to unpromote
     * @return 202 Accepted
     */
    @DeleteMapping(ApiConstants.ADMIN_TRIP_PROMOTE_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Unpromote a trip",
            description =
                    "Removes promotion from a trip. "
                            + "Returns 202 Accepted as the operation completes asynchronously.")
    @ApiResponse(responseCode = "202", description = "Trip unpromotion accepted")
    @ApiResponse(
            responseCode = "404",
            description = "Trip not found",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - valid JWT required",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden - ADMIN role required",
            content = @Content)
    public ResponseEntity<Void> unpromoteTrip(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Parameter(description = "Trip ID to unpromote", required = true) @PathVariable
                    UUID tripId) {
        log.info("Admin {} unpromoting trip {}", adminId, tripId);
        promotedTripService.unpromoteTrip(adminId, tripId);
        log.info("Accepted trip unpromotion request for trip {}", tripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Updates the donation link for a promoted trip.
     *
     * @param adminId the admin user performing the operation
     * @param tripId the ID of the promoted trip
     * @param request the new donation link
     * @return 202 Accepted with the promoted trip ID
     */
    @PutMapping(
            value = ApiConstants.ADMIN_TRIP_DONATION_LINK_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Update donation link",
            description =
                    "Updates the donation link for a promoted trip. "
                            + "Returns 202 Accepted with the promoted trip ID.")
    @ApiResponse(responseCode = "202", description = "Donation link updated successfully")
    @ApiResponse(
            responseCode = "404",
            description = "Promoted trip not found",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - valid JWT required",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden - ADMIN role required",
            content = @Content)
    public ResponseEntity<UUID> updateDonationLink(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Parameter(description = "Trip ID to update donation link for", required = true)
                    @PathVariable
                    UUID tripId,
            @Valid @RequestBody PromoteTripRequest request) {
        log.info("Admin {} updating donation link for trip {}", adminId, tripId);

        UUID promotedTripId =
                promotedTripService.updatePromotedTripDonationLink(
                        adminId, tripId, request != null ? request.donationLink() : null);

        log.info("Accepted donation link update request with ID: {}", promotedTripId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(promotedTripId);
    }

    /**
     * Deletes any trip regardless of ownership.
     *
     * @param adminId the admin user performing the operation
     * @param tripId the ID of the trip to delete
     * @return 204 No Content on success
     */
    @DeleteMapping(ApiConstants.ADMIN_TRIP_BY_ID_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete any trip",
            description =
                    "Deletes a trip and all associated data, regardless of ownership. "
                            + "Admin-only maintenance endpoint.")
    @ApiResponse(responseCode = "204", description = "Trip deleted successfully")
    @ApiResponse(
            responseCode = "404",
            description = "Trip not found",
            content = @Content(schema = @Schema(implementation = Map.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - valid JWT required",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Forbidden - ADMIN role required",
            content = @Content)
    public ResponseEntity<Void> deleteTrip(
            @Parameter(hidden = true) @CurrentUserId UUID adminId,
            @Parameter(description = "Trip ID to delete", required = true) @PathVariable
                    UUID tripId) {
        log.info("Admin {} deleting trip {}", adminId, tripId);
        tripService.adminDeleteTrip(adminId, tripId);
        return ResponseEntity.noContent().build();
    }
}
