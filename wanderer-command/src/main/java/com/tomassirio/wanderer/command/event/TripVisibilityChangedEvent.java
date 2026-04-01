package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripVisibilityChangedPayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripVisibilityChangedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private String newVisibility;
    private String previousVisibility;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_VISIBILITY_CHANGED;
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
        return TripVisibilityChangedPayload.builder()
                .tripId(tripId)
                .newVisibility(newVisibility)
                .previousVisibility(previousVisibility)
                .build();
    }
}
