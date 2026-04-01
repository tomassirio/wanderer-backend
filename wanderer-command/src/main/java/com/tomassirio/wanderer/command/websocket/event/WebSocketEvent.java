package com.tomassirio.wanderer.command.websocket.event;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketEvent {
    private String type;
    private UUID tripId;
    private String timestamp;
    private Object payload;

    public static WebSocketEvent create(String type, UUID tripId, Object payload) {
        return WebSocketEvent.builder()
                .type(type)
                .tripId(tripId)
                .timestamp(Instant.now().toString())
                .payload(payload)
                .build();
    }
}
