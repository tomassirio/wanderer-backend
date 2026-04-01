package com.tomassirio.wanderer.command.websocket.auth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

/**
 * Service that manages WebSocket authentication using a chain of responsibility pattern.
 *
 * <p>Strategies are tried in order until one successfully handles the authentication. The order
 * matters: TokenAuthenticationStrategy is tried first, then AnonymousAuthenticationStrategy as a
 * fallback for guest users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketAuthenticationService {

    private final List<WebSocketAuthenticationStrategy> strategies;

    /**
     * Authenticates a WebSocket session using the first applicable strategy.
     *
     * @param session the WebSocket session to authenticate
     * @return the authentication result
     */
    public AuthenticationResult authenticate(WebSocketSession session) {
        for (WebSocketAuthenticationStrategy strategy : strategies) {
            if (strategy.canHandle(session)) {
                AuthenticationResult result = strategy.authenticate(session);
                if (result.isAuthenticated()) {
                    log.debug(
                            "Session {} authenticated using {} (anonymous: {})",
                            session.getId(),
                            strategy.getClass().getSimpleName(),
                            result.isAnonymous());
                    return result;
                }
            }
        }

        // If no strategy succeeded, return failed result
        log.warn("All authentication strategies failed for session {}", session.getId());
        return AuthenticationResult.failed("Authentication failed");
    }
}
