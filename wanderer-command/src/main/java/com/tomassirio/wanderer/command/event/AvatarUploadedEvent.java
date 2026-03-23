package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.WebSocketEventType;
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
public class AvatarUploadedEvent implements DomainEvent, Broadcastable {
    private UUID userId;
    private byte[] fileBytes;
    private String contentType;
    private String originalFilename;

    @Override
    public String getEventType() {
        return WebSocketEventType.AVATAR_UPLOADED;
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
        return UserProfileUpdatedPayload.builder().userId(userId).build();
    }
}
