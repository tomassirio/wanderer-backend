package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripCreationRequest(
        @Schema(description = "Trip name", example = "Summer Road Trip 2025")
                @NotBlank(message = "Trip name is required")
                @Size(
                        min = 3,
                        max = 100,
                        message = "Trip name must be between 3 and 100 characters")
                String name,
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
        @Schema(
                        description = "Whether automatic location updates are enabled",
                        example = "true")
                Boolean automaticUpdates,
        @Schema(
                        description =
                                "Interval in minutes for automatic location updates",
                        example = "15")
                @Min(value = 15, message = "Update refresh must be at least 15 minutes")
                Integer updateRefresh) {}
