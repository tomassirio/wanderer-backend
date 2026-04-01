package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.FriendRequestPayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestDeclinedEvent implements DomainEvent, Broadcastable {
    private UUID requestId;
    private UUID senderId;
    private UUID receiverId;

    @Override
    public String getEventType() {
        return WebSocketEventType.FRIEND_REQUEST_DECLINED;
    }

    @Override
    public String getTopic() {
        return WebSocketEventType.userTopic(senderId);
    }

    @Override
    public UUID getTargetId() {
        return senderId;
    }

    @Override
    public Object toWebSocketPayload() {
        return FriendRequestPayload.builder()
                .requestId(requestId)
                .senderId(senderId)
                .receiverId(receiverId)
                .status("DECLINED")
                .build();
    }
}
