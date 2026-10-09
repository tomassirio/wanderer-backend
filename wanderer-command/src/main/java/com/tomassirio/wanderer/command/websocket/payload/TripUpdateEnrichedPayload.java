package com.tomassirio.wanderer.command.websocket.payload;

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
public class TripUpdateEnrichedPayload {
    private UUID tripId;
    private UUID tripUpdateId;
    private String city;
    private String country;
    private Double temperatureCelsius;
    private WeatherCondition weatherCondition;
}
