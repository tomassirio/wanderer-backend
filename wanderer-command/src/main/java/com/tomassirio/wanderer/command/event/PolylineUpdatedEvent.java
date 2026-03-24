package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.PolylineUpdatedPayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolylineUpdatedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private String encodedPolyline;

    @Override
    public String getEventType() {
        return WebSocketEventType.POLYLINE_UPDATED;
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
        return PolylineUpdatedPayload.builder()
                .tripId(tripId)
                .encodedPolyline(encodedPolyline)
                .build();
    }
}
