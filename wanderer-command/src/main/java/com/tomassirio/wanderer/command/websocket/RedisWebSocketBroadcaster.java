package com.tomassirio.wanderer.command.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts WebSocket messages across multiple pods using Redis Pub/Sub.
 *
 * <p>When a message needs to be broadcast to a topic, this component publishes it to Redis. All
 * pods (including the sender) subscribe to Redis and deliver messages to their local WebSocket
 * sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWebSocketBroadcaster {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Publishes a WebSocket message to Redis for multi-pod broadcasting.
     *
     * @param topic the WebSocket topic (e.g., "/topic/trips/123")
     * @param message the JSON message to broadcast
     */
    public void publishToRedis(String topic, String message) {
        try {
            String redisChannel = getRedisChannel(topic);
            redisTemplate.convertAndSend(redisChannel, message);
            log.debug("Published message to Redis channel: {}", redisChannel);
        } catch (Exception e) {
            log.error("Error publishing message to Redis for topic: {}", topic, e);
        }
    }

    /**
     * Converts a WebSocket topic to a Redis channel name.
     *
     * @param topic the WebSocket topic
     * @return the Redis channel name
     */
    public static String getRedisChannel(String topic) {
        // Use a prefix to avoid conflicts with other Redis keys
        return "websocket:" + topic;
    }
}
