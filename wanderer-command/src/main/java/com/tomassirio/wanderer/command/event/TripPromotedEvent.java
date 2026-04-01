package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripLifecyclePayload;
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
public class TripPromotedEvent implements DomainEvent, Broadcastable {
    private UUID id;
    private UUID tripId;
    private String donationLink;
    private UUID promotedBy;
    private Instant promotedAt;
    private boolean preAnnounced;
    private Instant countdownStartDate;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_METADATA_UPDATED;
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
                .tripName(null) // Not needed for this event
                .ownerId(null)
                .visibility(null)
                .build();
    }
}
