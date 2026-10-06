package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.command.analytics.TripStartFunnel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AnalyticsEventRequest(
        @Schema(
                        description = "Client-side funnel event (TRIP_STARTED is server-emitted)",
                        example = "READY_SCREEN_VIEWED",
                        allowableValues = {
                            "READY_SCREEN_VIEWED",
                            "CLOSED_WITHOUT_STARTING",
                            "SAVED_AS_PLAN"
                        })
                @NotNull(message = "event is required")
                TripStartFunnel.Event event,
        @Schema(
                        description = "Where the trip would start from",
                        example = "SCRATCH",
                        allowableValues = {"SCRATCH", "PLAN"})
                TripStartFunnel.Source source) {

    @Schema(hidden = true)
    @AssertTrue(message = "TRIP_STARTED is recorded by the server")
    public boolean isClientEvent() {
        return event != TripStartFunnel.Event.TRIP_STARTED;
    }
}
