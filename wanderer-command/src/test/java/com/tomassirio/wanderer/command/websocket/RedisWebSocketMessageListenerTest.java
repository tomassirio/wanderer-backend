package com.tomassirio.wanderer.command.websocket;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

@ExtendWith(MockitoExtension.class)
class RedisWebSocketMessageListenerTest {

    @Mock private WebSocketSessionManager sessionManager;

    private RedisWebSocketMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new RedisWebSocketMessageListener(sessionManager);
    }

    @Test
    void onMessage_shouldExtractTopicAndBroadcastToLocalSessions() {
        // Given
        String channel = "websocket:/topic/trips/123";
        String messageBody = "{\"eventType\":\"TRIP_UPDATED\",\"payload\":{}}";
        Message message = new DefaultMessage(channel.getBytes(), messageBody.getBytes());

        // When
        listener.onMessage(message, null);

        // Then
        verify(sessionManager).broadcastToLocalSessions(eq("/topic/trips/123"), eq(messageBody));
    }

    @Test
    void onMessage_withUserTopic_shouldBroadcastCorrectly() {
        // Given
        String channel = "websocket:/topic/users/456";
        String messageBody = "{\"eventType\":\"FRIEND_REQUEST\",\"payload\":{}}";
        Message message = new DefaultMessage(channel.getBytes(), messageBody.getBytes());

        // When
        listener.onMessage(message, null);

        // Then
        verify(sessionManager).broadcastToLocalSessions(eq("/topic/users/456"), eq(messageBody));
    }
}
