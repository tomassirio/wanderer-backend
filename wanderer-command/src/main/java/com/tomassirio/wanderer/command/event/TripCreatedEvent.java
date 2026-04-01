package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripLifecyclePayload;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripModality;
import java.time.Instant;
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
public class TripCreatedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private String tripName;
    private UUID ownerId;
    private String visibility;
    private UUID tripPlanId;
    private Instant creationTimestamp;
    // Additional fields for createTripFromPlan
    private GeoLocation startLocation;
    private GeoLocation endLocation;
    private List<GeoLocation> waypoints;
    private Instant startTimestamp;
    private Instant endTimestamp;
    private TripModality tripModality;
    private String plannedPolyline;
    private Boolean automaticUpdates;
    private Integer updateRefresh;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_CREATED;
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
                .tripName(tripName)
                .ownerId(ownerId)
                .visibility(visibility)
                .build();
    }
}
