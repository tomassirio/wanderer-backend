package com.tomassirio.wanderer.command.websocket.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for WebSocket authentication strategies.
 *
 * <p>Defines the order in which authentication strategies are tried. Order matters: strategies are
 * tried sequentially until one succeeds.
 */
@Configuration
public class WebSocketAuthenticationConfig {

    /**
     * Defines the ordered list of authentication strategies.
     *
     * <p>Strategy order:
     *
     * <ol>
     *   <li>TokenAuthenticationStrategy - tries JWT token authentication first
     *   <li>AnonymousAuthenticationStrategy - fallback for guest users
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
                tokenStrategy, // Try authenticated users first
                anonymousStrategy // Fall back to anonymous for guests
                );
    }
}
