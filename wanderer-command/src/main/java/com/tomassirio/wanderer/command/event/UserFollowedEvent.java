package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.UserFollowPayload;
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
public class UserFollowedEvent implements DomainEvent, Broadcastable {
    private UUID followId;
    private UUID followerId;
    private UUID followedId;
    private Instant createdAt;

    @Override
    public String getEventType() {
        return WebSocketEventType.USER_FOLLOWED;
    }

    @Override
    public String getTopic() {
        return WebSocketEventType.userTopic(followedId);
    }

    @Override
    public UUID getTargetId() {
        return followedId;
    }

    @Override
    public Object toWebSocketPayload() {
        return UserFollowPayload.builder()
                .followId(followId)
                .followerId(followerId)
                .followedId(followedId)
                .build();
    }
}
