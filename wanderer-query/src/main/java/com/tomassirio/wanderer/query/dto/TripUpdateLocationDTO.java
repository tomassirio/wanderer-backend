package com.tomassirio.wanderer.query.dto;

import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Lightweight DTO for map marker rendering and timeline display. Contains only the fields needed to
 * place markers on a map and populate timeline entries, keeping the payload small by excluding
 * heavy fields like {@code message} (TEXT) and {@code reactions} (JSONB).
 *
 * @since 0.10.2
 */
@Schema(
        description =
                "Lightweight location data for rendering trip update markers on a map and"
                        + " populating the timeline sidebar")
public record TripUpdateLocationDTO(
        @Schema(description = "Unique identifier of the trip update") String id,
        @Schema(description = "Latitude of the location", example = "42.8805") Double lat,
        @Schema(description = "Longitude of the location", example = "-8.5449") Double lon,
        @Schema(description = "Timestamp of the update") Instant timestamp,
        @Schema(description = "Type of update (e.g., DAY_START, DAY_END, REGULAR)")
                UpdateType updateType,
        @Schema(description = "Battery percentage at the time of the update", example = "71")
                Integer battery,
        @Schema(description = "City name from reverse geocoding", example = "Utrecht") String city,
        @Schema(description = "Country name from reverse geocoding", example = "Netherlands")
                String country,
        @Schema(description = "Temperature in Celsius at the time of the update", example = "8.7")
                Double temperatureCelsius,
        @Schema(description = "Weather condition at the time of the update")
                WeatherCondition weatherCondition) {}
