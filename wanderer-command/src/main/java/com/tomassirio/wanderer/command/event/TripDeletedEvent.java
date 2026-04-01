package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripLifecyclePayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDeletedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private UUID ownerId;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_DELETED;
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
        return TripLifecyclePayload.builder()
                .tripId(tripId)
                .ownerId(ownerId)
                .tripName(null)
                .visibility(null)
                .build();
    }
}
