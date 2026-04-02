package com.tomassirio.wanderer.command.websocket.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Authentication strategy for anonymous/guest WebSocket connections.
 *
 * <p>This strategy allows unauthenticated users to connect and subscribe to public trip updates.
 * Anonymous connections are subject to stricter rate limits and can only subscribe to a limited
 * number of topics.
 */
@Slf4j
@Component
public class AnonymousAuthenticationStrategy implements WebSocketAuthenticationStrategy {

    @Override
    public boolean canHandle(WebSocketSession session) {
        // This strategy can always handle any session as a fallback
        return true;
    }

    @Override
    public AuthenticationResult authenticate(WebSocketSession session) {
        return AuthenticationResult.anonymous();
    }
}
