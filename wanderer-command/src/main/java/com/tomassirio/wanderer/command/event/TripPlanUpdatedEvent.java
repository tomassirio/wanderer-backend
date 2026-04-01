package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPlanUpdatedEvent implements DomainEvent, Broadcastable {
    private UUID tripPlanId;
    private UUID userId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private GeoLocation startLocation;
    private GeoLocation endLocation;
    private List<GeoLocation> waypoints;
    private String plannedPolyline;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_PLAN_UPDATED;
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
