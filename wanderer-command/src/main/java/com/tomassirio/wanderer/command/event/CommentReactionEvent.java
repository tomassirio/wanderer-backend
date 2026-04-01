package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.CommentReactionPayload;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentReactionEvent implements DomainEvent, Broadcastable {
    private UUID tripId;
    private UUID commentId;
    private String reactionType;
    private UUID userId;
    private boolean added; // true for added, false for removed
    private String previousReactionType; // Set when replacing a reaction

    @Override
    public String getEventType() {
        if (previousReactionType != null) {
            return WebSocketEventType.COMMENT_REACTION_REPLACED;
        }
        return added
                ? WebSocketEventType.COMMENT_REACTION_ADDED
                : WebSocketEventType.COMMENT_REACTION_REMOVED;
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
        CommentReactionPayload.CommentReactionPayloadBuilder builder =
                CommentReactionPayload.builder()
                        .tripId(tripId)
                        .commentId(commentId)
                        .reactionType(reactionType)
                        .userId(userId);

        if (previousReactionType != null) {
            builder.previousReactionType(previousReactionType);
        }

        return builder.build();
    }
}
