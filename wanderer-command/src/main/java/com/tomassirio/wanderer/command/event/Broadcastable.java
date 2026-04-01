package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import java.util.UUID;

/**
 * Interface for events that should be broadcast via WebSocket after persistence.
 *
 * <p>Events implementing this interface will automatically be broadcast to the appropriate topic
 * after being handled by their persistence handler.
 */
public interface Broadcastable {

    /**
     * Returns the WebSocket event type constant.
     *
     * @return the event type string (use constants from {@link WebSocketEventType})
     */
    String getEventType();

    /**
     * Returns the WebSocket topic to broadcast to.
     *
     * @return the topic string (e.g., "/topic/trips/{id}" or "/topic/users/{id}")
     */
    String getTopic();

    /**
     * Returns the target ID for the broadcast (trip ID or user ID).
     *
     * @return the target UUID
     */
    UUID getTargetId();

    /**
     * Returns the payload to broadcast via WebSocket.
     *
     * @return the payload object to be serialized and sent
     */
    Object toWebSocketPayload();
}
