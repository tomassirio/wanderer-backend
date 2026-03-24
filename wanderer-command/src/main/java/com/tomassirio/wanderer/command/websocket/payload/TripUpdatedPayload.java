package com.tomassirio.wanderer.command.websocket.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripUpdatedPayload {
    private UUID tripId;
    private Double latitude;
    private Double longitude;
    private Integer batteryLevel;
    private String message;
    private String city;
    private String country;
    private Double temperatureCelsius;
    private WeatherCondition weatherCondition;
    private UpdateType updateType;
    private Double distanceSoFarKm;
}
