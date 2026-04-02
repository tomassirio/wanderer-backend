package com.tomassirio.wanderer.command.config;

import com.tomassirio.wanderer.command.websocket.RedisWebSocketMessageListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis-based WebSocket message broadcasting across multiple pods.
 *
 * <p>This configuration sets up:
 * - Redis Pub/Sub listener for receiving WebSocket broadcast messages
 * - Pattern subscription to all websocket:* channels
 */
@Configuration
@RequiredArgsConstructor
public class RedisWebSocketConfig {

    private final RedisWebSocketMessageListener redisWebSocketMessageListener;

    /**
     * Creates a Redis message listener container that listens for WebSocket broadcast messages.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Subscribe to all websocket:* channels
        MessageListenerAdapter adapter = new MessageListenerAdapter(redisWebSocketMessageListener);
        container.addMessageListener(adapter, new ChannelTopic("websocket:*"));

        return container;
    }

    /**
     * Redis template for publishing WebSocket messages.
     * Uses String serializer for both keys and values since we're sending JSON.
     */
    @Bean
    public RedisTemplate<String, String> webSocketRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializers for both keys and values
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
