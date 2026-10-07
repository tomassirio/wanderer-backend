package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body of {@code POST /trips/start}: create a trip, make it live and record the first check-in. */
public record StartTripRequest(
        @Schema(
                        description =
                                "Trip name. Required unless tripPlanId is set (then ignored).",
                        example = "Camino day 1")
                @Size(
                        min = 3,
                        max = 100,
                        message = "Trip name must be between 3 and 100 characters")
                String name,
        @Schema(
                        description = "Trip visibility (public / friends / only me)",
                        example = "PUBLIC",
                        allowableValues = {"PRIVATE", "PROTECTED", "PUBLIC"})
                @NotNull(message = "Visibility is required")
                TripVisibility visibility,
        @Schema(
                        description =
                                "Single-day or multi-day. Default SIMPLE. Ignored when tripPlanId"
                                        + " is set (plan type wins).",
                        example = "SIMPLE",
                        allowableValues = {"SIMPLE", "MULTI_DAY"})
                TripModality tripModality,
        @Schema(description = "Whether automatic check-ins are enabled", example = "true")
                @NotNull(message = "automaticUpdates is required")
                Boolean automaticUpdates,
        @Schema(
                        description =
                                "Automatic check-in interval in seconds. Default 900 when"
                                        + " automaticUpdates is true.",
                        example = "900")
                @Min(value = 60, message = "Update refresh must be at least 60 seconds")
                Integer updateRefresh,
        @Schema(description = "Current location, recorded as the TRIP_STARTED check-in")
                @Valid
                @NotNull(message = "Location is required")
                GeoLocation location,
        @Schema(description = "Battery percentage", example = "82")
                @Min(value = 0, message = "Battery must be between 0 and 100")
                @Max(value = 100, message = "Battery must be between 0 and 100")
                Integer battery,
        @Schema(description = "Message on the first check-in", example = "Buen Camino!")
                @Size(max = 500, message = "Message must not exceed 500 characters")
                String message,
        @Schema(
                        description =
                                "Optional plan to start from: name, route, dates and type come"
                                        + " from the plan")
                UUID tripPlanId) {

    @Schema(hidden = true)
    @AssertTrue(message = "Trip name is required when no tripPlanId is given")
    public boolean isNamePresentOrPlanGiven() {
        return tripPlanId != null || (name != null && !name.isBlank());
    }
}
