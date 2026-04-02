package com.tomassirio.wanderer.command.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.command.event.Broadcastable;
import com.tomassirio.wanderer.command.websocket.event.WebSocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for broadcasting WebSocket events to subscribers across all pods.
 *
 * <p>This service provides a simple interface for broadcasting events that implement {@link
 * Broadcastable}. The event itself knows its topic, event type, and payload.
 * 
 * <p>Messages are broadcast to all pods via Redis Pub/Sub, ensuring that all connected
 * clients receive updates regardless of which pod they're connected to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketEventService {

    private final RedisWebSocketBroadcaster redisBroadcaster;
    private final ObjectMapper objectMapper;

    /**
     * Broadcasts a Broadcastable event to its designated topic across all pods.
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
            
            // Publish to Redis - all pods (including this one) will receive and broadcast to their local sessions
            redisBroadcaster.publishToRedis(topic, message);
            
            log.info("Published {} event to Redis for topic {}", event.getEventType(), topic);
        } catch (JsonProcessingException e) {
            log.error("Error serializing WebSocket event: {}", event.getEventType(), e);
        }
    }
}
