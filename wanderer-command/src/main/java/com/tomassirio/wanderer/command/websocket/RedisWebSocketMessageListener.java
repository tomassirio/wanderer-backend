package com.tomassirio.wanderer.command.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Listens for WebSocket messages published to Redis and broadcasts them to local WebSocket sessions.
 *
 * <p>This listener receives messages from all pods (including the same pod that published).
 * When a message arrives, it extracts the WebSocket topic and broadcasts to local sessions subscribed to that topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWebSocketMessageListener implements MessageListener {

    private final WebSocketSessionManager sessionManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String messageBody = new String(message.getBody());

            // Extract the WebSocket topic from the Redis channel name
            // Channel format: "websocket:/topic/trips/123"
            String topic = extractTopicFromChannel(channel);

            log.debug("Received Redis message for topic: {} (channel: {})", topic, channel);

            // Broadcast to local WebSocket sessions subscribed to this topic
            sessionManager.broadcastToLocalSessions(topic, messageBody);

        } catch (Exception e) {
            log.error("Error processing Redis WebSocket message", e);
        }
    }

    /**
     * Extracts the WebSocket topic from the Redis channel name.
     *
     * @param channel the Redis channel (e.g., "websocket:/topic/trips/123")
     * @return the WebSocket topic (e.g., "/topic/trips/123")
     */
    private String extractTopicFromChannel(String channel) {
        // Remove the "websocket:" prefix
        if (channel.startsWith("websocket:")) {
            return channel.substring("websocket:".length());
        }
        return channel;
    }
}
