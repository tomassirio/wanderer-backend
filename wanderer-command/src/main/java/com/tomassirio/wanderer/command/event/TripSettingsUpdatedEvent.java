package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.TripSettingsUpdatedPayload;
import com.tomassirio.wanderer.commons.domain.TripModality;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSettingsUpdatedEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private Integer updateRefresh;
    private Boolean automaticUpdates;
    private TripModality tripModality;

    @Override
    public String getEventType() {
        return WebSocketEventType.TRIP_SETTINGS_UPDATED;
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
        return TripSettingsUpdatedPayload.builder()
                .tripId(tripId)
                .updateRefresh(updateRefresh)
                .automaticUpdates(automaticUpdates)
                .tripModality(tripModality)
                .build();
    }
}
