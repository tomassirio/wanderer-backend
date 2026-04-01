package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import com.tomassirio.wanderer.command.websocket.payload.AchievementUnlockedPayload;
import com.tomassirio.wanderer.commons.domain.AchievementType;
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
public class AchievementUnlockedEvent implements DomainEvent, Broadcastable {
    private UUID userAchievementId;
    private UUID userId;
    private UUID achievementId;
    private UUID tripId;
    private AchievementType achievementType;
    private String achievementName;
    private Double valueAchieved;
    private Instant unlockedAt;

    @Override
    public String getEventType() {
        return WebSocketEventType.ACHIEVEMENT_UNLOCKED;
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
        return AchievementUnlockedPayload.builder()
                .achievementId(achievementId)
                .achievementType(achievementType)
                .achievementName(achievementName)
                .tripId(tripId)
                .valueAchieved(valueAchieved)
                .unlockedAt(unlockedAt)
                .build();
    }
}
