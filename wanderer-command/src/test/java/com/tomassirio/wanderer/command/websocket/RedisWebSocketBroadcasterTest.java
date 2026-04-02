package com.tomassirio.wanderer.command.websocket;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisWebSocketBroadcasterTest {

    @Mock private RedisTemplate<String, String> redisTemplate;

    private RedisWebSocketBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new RedisWebSocketBroadcaster(redisTemplate);
    }

    @Test
    void publishToRedis_shouldPublishMessageToRedisChannel() {
        // Given
        String topic = "/topic/trips/123";
        String message = "{\"eventType\":\"TRIP_UPDATED\",\"payload\":{}}";

        // When
        broadcaster.publishToRedis(topic, message);

        // Then
        verify(redisTemplate).convertAndSend(eq("websocket:/topic/trips/123"), eq(message));
    }

    @Test
    void publishToRedis_withUserTopic_shouldPublishToCorrectChannel() {
        // Given
        String topic = "/topic/users/456";
        String message = "{\"eventType\":\"FRIEND_REQUEST\",\"payload\":{}}";

        // When
        broadcaster.publishToRedis(topic, message);

        // Then
        verify(redisTemplate).convertAndSend(eq("websocket:/topic/users/456"), eq(message));
    }
}
