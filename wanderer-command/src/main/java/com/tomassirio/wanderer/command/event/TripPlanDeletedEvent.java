package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPlanDeletedEvent implements DomainEvent, Broadcastable {
    private UUID tripPlanId;
    private UUID userId;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_PLAN_DELETED;
    }

    @Override
    public String getTopic() {
        return WebSocketEventType.userTopic(userId);
    }

    @Override
    public UUID getTargetId() {
        return userId;
    }

    @Override
    public Object toWebSocketPayload() {
        return this;
    }
}
