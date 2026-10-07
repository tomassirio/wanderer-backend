package com.tomassirio.wanderer.command.controller;

import com.tomassirio.wanderer.command.analytics.TripStartFunnel;
import com.tomassirio.wanderer.command.controller.request.AnalyticsEventRequest;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Records client-side trip-start funnel events as Micrometer counters. */
@RestController
@RequestMapping(
        value = ApiConstants.ANALYTICS_EVENTS_PATH,
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "Client funnel events (exported as Prometheus counters)")
public class AnalyticsController {

    private final TripStartFunnel tripStartFunnel;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Record a trip-start funnel event",
            description =
                    "Increments wanderer_trip_start_funnel_total{event,source}. TRIP_STARTED is"
                            + " emitted by POST /trips/start and rejected here.")
    @ApiResponse(responseCode = "204", description = "Event recorded")
    @ApiResponse(responseCode = "400", description = "Unknown or server-only event")
    public ResponseEntity<Void> recordEvent(@Valid @RequestBody AnalyticsEventRequest request) {
        tripStartFunnel.record(request.event(), request.source());
        return ResponseEntity.noContent().build();
    }
}
