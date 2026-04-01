package com.tomassirio.wanderer.command.websocket.config;

import java.util.List;

import com.tomassirio.wanderer.command.websocket.auth.WebSocketAuthenticationStrategy;
import com.tomassirio.wanderer.command.websocket.auth.strategy.AnonymousAuthenticationStrategy;
import com.tomassirio.wanderer.command.websocket.auth.strategy.TokenAuthenticationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for WebSocket authentication strategies.
 * 
 * <p>Defines the order in which authentication strategies are tried.
 * Order matters: strategies are tried sequentially until one succeeds.
 */
@Configuration
public class WebSocketAuthenticationConfig {
    
    /**
     * Defines the ordered list of authentication strategies.
     * 
     * <p>Strategy order:
     * <ol>
     *   <li>TokenAuthenticationStrategy - tries JWT token authentication first</li>
     *   <li>AnonymousAuthenticationStrategy - fallback for guest users</li>
     * </ol>
     * 
     * @param tokenStrategy the JWT token authentication strategy
     * @param anonymousStrategy the anonymous authentication strategy
     * @return ordered list of authentication strategies
     */
    @Bean
    public List<WebSocketAuthenticationStrategy> authenticationStrategies(
            TokenAuthenticationStrategy tokenStrategy,
            AnonymousAuthenticationStrategy anonymousStrategy) {
        return List.of(
                tokenStrategy,      // Try authenticated users first
                anonymousStrategy   // Fall back to anonymous for guests
        );
    }
}
