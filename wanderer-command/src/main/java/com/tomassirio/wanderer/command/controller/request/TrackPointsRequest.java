package com.tomassirio.wanderer.command.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrackPointsRequest(
        @Schema(description = "Recorded GPS fixes, 1 to 500 per request")
                @NotEmpty(message = "At least one point is required")
                @Size(max = 500, message = "At most 500 points per request")
                List<@Valid @NotNull Point> points) {

    public record Point(
            @Schema(description = "Client-generated point ID (idempotency key)")
                    @NotNull(message = "Point id is required")
                    UUID id,
            @Schema(example = "52.09")
                    @NotNull(message = "Latitude is required")
                    @DecimalMin("-90.0")
                    @DecimalMax("90.0")
                    Double lat,
            @Schema(example = "5.12")
                    @NotNull(message = "Longitude is required")
                    @DecimalMin("-180.0")
                    @DecimalMax("180.0")
                    Double lon,
            @Schema(description = "Horizontal accuracy in meters", example = "8.0") @PositiveOrZero
                    Double accuracyM,
            @Schema(description = "Altitude in meters", example = "3.1") Double altitudeM,
            @Schema(
                            description = "When the phone captured the fix",
                            example = "2026-10-08T09:15:02Z")
                    @NotNull(message = "recordedAt is required")
                    Instant recordedAt) {}
}
