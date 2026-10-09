package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripUpdateEnrichedPayload;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Published once a check-in has been asynchronously enriched with place name and weather. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUpdateEnrichedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private UUID tripUpdateId;
    private String city;
    private String country;
    private Double temperatureCelsius;
    private WeatherCondition weatherCondition;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_UPDATE_ENRICHED;
    }

    @Override
    public String getTopic() {
        return WebSocketEventType.tripTopic(tripId);
    }

    @Override
    public UUID getTargetId() {
        return tripId;
    }

    @Override
    public Object toWebSocketPayload() {
        return TripUpdateEnrichedPayload.builder()
                .tripId(tripId)
                .tripUpdateId(tripUpdateId)
                .city(city)
                .country(country)
                .temperatureCelsius(temperatureCelsius)
                .weatherCondition(weatherCondition)
                .build();
    }
}
