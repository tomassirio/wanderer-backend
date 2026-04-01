package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.FriendRequestPayload;
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
public class FriendRequestSentEvent implements DomainEvent, Broadcastable {
    private UUID requestId;
    private UUID senderId;
    private UUID receiverId;
    private String status;
    private Instant createdAt;

    @Override
    public String getEventType() {
        return WebSocketEventType.FRIEND_REQUEST_RECEIVED;
    }

    @Override
    public String getTopic() {
        return WebSocketEventType.userTopic(receiverId);
    }

    @Override
    public UUID getTargetId() {
        return receiverId;
    }

    @Override
    public Object toWebSocketPayload() {
        return FriendRequestPayload.builder()
                .requestId(requestId)
                .senderId(senderId)
                .receiverId(receiverId)
                .status(status)
                .build();
    }
}
