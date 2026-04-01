package com.tomassirio.wanderer.command.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.command.event.Broadcastable;
import com.tomassirio.wanderer.command.websocket.event.WebSocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for broadcasting WebSocket events to subscribers.
 *
 * <p>This service provides a simple interface for broadcasting events that implement {@link
 * Broadcastable}. The event itself knows its topic, event type, and payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketEventService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * Broadcasts a Broadcastable event to its designated topic.
     *
     * @param event the event to broadcast (must implement {@link Broadcastable})
     */
    public void broadcast(Broadcastable event) {
        String topic = event.getTopic();
        WebSocketEvent wsEvent =
                WebSocketEvent.create(
                        event.getEventType(), event.getTargetId(), event.toWebSocketPayload());

        try {
            String message = objectMapper.writeValueAsString(wsEvent);
            sessionManager.broadcast(topic, message);
            log.info(
                    "Broadcast {} event to {} ({} subscribers)",
                    event.getEventType(),
                    topic,
                    sessionManager.getSubscribersCount(topic));
        } catch (JsonProcessingException e) {
            log.error("Error serializing WebSocket event: {}", event.getEventType(), e);
        }
    }
}
