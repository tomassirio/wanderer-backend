package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPlanCreatedEvent implements DomainEvent, Broadcastable {
    private UUID tripPlanId;
    private UUID userId;
    private String name;
    private TripPlanType planType;
    private LocalDate startDate;
    private LocalDate endDate;
    private GeoLocation startLocation;
    private GeoLocation endLocation;
    private List<GeoLocation> waypoints;
    private Map<String, Object> metadata;
    private Instant createdTimestamp;
    private String plannedPolyline;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_PLAN_CREATED;
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
