package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record TripUpdateCreationRequest(
        @Schema(description = "Current location. Required for REGULAR check-ins.") @Valid
                GeoLocation location,
        @Schema(description = "Battery percentage", example = "75")
                @Min(value = 0, message = "Battery must be between 0 and 100")
                @Max(value = 100, message = "Battery must be between 0 and 100")
                Integer battery,
        @Schema(description = "Optional message or note", example = "Reached checkpoint")
                @Size(max = 500, message = "Message must not exceed 500 characters")
                String message,
        @Schema(
                        description =
                                "Type of update: REGULAR, DAY_START, DAY_END, TRIP_STARTED, or TRIP_ENDED",
                        example = "REGULAR",
                        allowableValues = {
                            "REGULAR",
                            "DAY_START",
                            "DAY_END",
                            "TRIP_STARTED",
                            "TRIP_ENDED"
                        })
                UpdateType updateType,
        @Schema(
                        description =
                                "Optional client-generated ID. Re-sending a check-in with an ID"
                                        + " that already exists is a no-op that returns the same ID.")
                UUID id,
        @Schema(
                        description =
                                "When the phone captured the check-in. Defaults to the server time;"
                                        + " rejected if more than 5 minutes in the future.",
                        example = "2026-10-08T09:15:02Z")
                Instant recordedAt) {

    /** Convenience constructor for server-side check-ins (no client ID, captured now). */
    public TripUpdateCreationRequest(
            GeoLocation location, Integer battery, String message, UpdateType updateType) {
        this(location, battery, message, updateType, null, null);
    }
}
