package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.FriendRequestPayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event representing the cancellation of a friend request by the sender.
 *
 * <p>This event is published when a user cancels a pending friend request they previously sent. The
 * event notifies the receiver that the request has been cancelled.
 *
 * @author tomassirio
 * @since 0.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestCancelledEvent implements DomainEvent, Broadcastable {
    private UUID requestId;
    private UUID senderId;
    private UUID receiverId;

    @Override
    public String getEventType() {
        return WebSocketEventType.FRIEND_REQUEST_CANCELLED;
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
                .status("CANCELLED")
                .build();
    }
}
