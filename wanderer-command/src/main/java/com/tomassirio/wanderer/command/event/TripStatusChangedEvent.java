package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripStatusChangedPayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripStatusChangedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private String newStatus;
    private String previousStatus;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_STATUS_CHANGED;
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
        return TripStatusChangedPayload.builder()
                .tripId(tripId)
                .newStatus(newStatus)
                .previousStatus(previousStatus)
                .build();
    }
}
