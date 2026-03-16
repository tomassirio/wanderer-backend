package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TripFromPlanRequest(
        @Schema(
                        description = "Trip visibility",
                        example = "PUBLIC",
                        allowableValues = {"PRIVATE", "PROTECTED", "PUBLIC"})
                @NotNull(message = "Visibility is required")
                TripVisibility visibility,
        @Schema(
                        description = "Trip modality",
                        example = "SIMPLE",
                        allowableValues = {"SIMPLE", "MULTI_DAY"})
                TripModality tripModality,
        @Schema(description = "Whether automatic location updates are enabled", example = "true")
                Boolean automaticUpdates,
        @Schema(description = "Interval in minutes for automatic location updates", example = "15")
                @Min(value = 15, message = "Update refresh must be at least 15 minutes")
                Integer updateRefresh) {}
