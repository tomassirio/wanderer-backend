package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.UserProfileUpdatedPayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsUpdatedEvent implements DomainEvent, Broadcastable {
    private UUID userId;
    private String displayName;
    private String bio;

    @Override
    public String getEventType() {
        return WebSocketEventType.USER_PROFILE_UPDATED;
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
        return UserProfileUpdatedPayload.builder()
                .userId(userId)
                .displayName(displayName)
                .bio(bio)
                .build();
    }
}
