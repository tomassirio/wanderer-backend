package com.tomassirio.wanderer.command.websocket.auth;

import org.springframework.web.socket.WebSocketSession;

/**
 * Strategy interface for WebSocket authentication.
 *
 * <p>Implementations of this interface define different authentication strategies for WebSocket
 * connections (e.g., authenticated users, anonymous guests).
 */
public interface WebSocketAuthenticationStrategy {

    /**
     * Attempts to authenticate a WebSocket session.
     *
     * @param session the WebSocket session to authenticate
     * @return an AuthenticationResult containing the userId (null for anonymous) and authentication
     *     status
     */
    AuthenticationResult authenticate(WebSocketSession session);

    /**
     * Checks if this strategy can handle the given WebSocket session.
     *
     * @param session the WebSocket session
     * @return true if this strategy can authenticate the session
     */
    boolean canHandle(WebSocketSession session);
}
