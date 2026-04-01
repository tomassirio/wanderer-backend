package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripUpdatedPayload;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUpdatedEvent implements DomainEvent, Broadcastable {
    private UUID tripUpdateId;
    private UUID tripId;
    private GeoLocation location;
    private Integer batteryLevel;
    private String message;
    private String city;
    private String country;
    private Double temperatureCelsius;
    private WeatherCondition weatherCondition;
    private UpdateType updateType;
    private Double distanceSoFarKm;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_UPDATED;
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
        return TripUpdatedPayload.builder()
                .tripId(tripId)
                .latitude(location != null ? location.getLat() : null)
                .longitude(location != null ? location.getLon() : null)
                .batteryLevel(batteryLevel)
                .message(message)
                .city(city)
                .country(country)
                .temperatureCelsius(temperatureCelsius)
                .weatherCondition(weatherCondition)
                .updateType(updateType)
                .distanceSoFarKm(distanceSoFarKm)
                .build();
    }
}
