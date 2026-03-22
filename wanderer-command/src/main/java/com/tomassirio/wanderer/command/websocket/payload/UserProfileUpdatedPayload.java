package com.tomassirio.wanderer.command.websocket.payload;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdatedPayload {
    private UUID userId;
    private String displayName;
    private String bio;
}
