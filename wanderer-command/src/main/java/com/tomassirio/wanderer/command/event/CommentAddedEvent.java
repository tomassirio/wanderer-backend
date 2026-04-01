package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.CommentAddedPayload;
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
public class CommentAddedEvent implements DomainEvent, Broadcastable {
    private UUID commentId;
    private UUID tripId;
    private UUID userId;
    private String username;
    private String message;
    private UUID parentCommentId;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return WebSocketEventType.COMMENT_ADDED;
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
        return CommentAddedPayload.create(
                tripId, commentId, userId, username, message, parentCommentId);
    }
}
