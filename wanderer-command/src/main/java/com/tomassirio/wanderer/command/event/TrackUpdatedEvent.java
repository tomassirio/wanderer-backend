package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TrackUpdatedPayload;
import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Broadcast after a trip's track was recomputed with newly accepted points. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackUpdatedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private List<TrackPointDTO> points;
    private Double distanceKm;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRACK_UPDATED;
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
        return TrackUpdatedPayload.builder()
                .tripId(tripId)
                .points(points)
                .distanceKm(distanceKm)
                .build();
    }
}
