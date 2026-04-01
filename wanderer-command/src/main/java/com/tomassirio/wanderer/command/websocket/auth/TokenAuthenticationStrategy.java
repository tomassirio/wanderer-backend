package com.tomassirio.wanderer.command.websocket.auth;

import com.tomassirio.wanderer.command.websocket.auth.AuthenticationResult;
import com.tomassirio.wanderer.command.websocket.auth.WebSocketAuthenticationStrategy;
import com.tomassirio.wanderer.commons.security.JwtUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Authentication strategy for JWT token-based WebSocket connections.
 *
 * <p>This strategy attempts to extract and validate a JWT token from the WebSocket query
 * parameters. If successful, the user is authenticated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthenticationStrategy implements WebSocketAuthenticationStrategy {

    private final JwtUtils jwtUtils;

    @Override
    public boolean canHandle(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query == null || query.isEmpty()) {
            return false;
        }

        Map<String, String> params = parseQueryParams(query);
        String token = params.get("token");
        return token != null && !token.isEmpty();
    }

    @Override
    public AuthenticationResult authenticate(WebSocketSession session) {
        try {
            String query = session.getUri() != null ? session.getUri().getQuery() : "";
            Map<String, String> params = parseQueryParams(query);
            String token = params.get("token");

            if (token == null || token.isEmpty()) {
                return AuthenticationResult.failed("Token parameter is empty");
            }

            UUID userId = validateToken(token);
            log.debug(
                    "Token authentication successful for session: {}, userId: {}",
                    session.getId(),
                    userId);
            return AuthenticationResult.authenticated(userId);

        } catch (Exception e) {
            log.warn(
                    "Token authentication failed for session {}: {}",
                    session.getId(),
                    e.getMessage());
            return AuthenticationResult.failed(e.getMessage());
        }
    }

    private UUID validateToken(String token) {
        Map<String, Object> payload = jwtUtils.parsePayload(token);
        Object sub = payload.get("sub");
        if (sub == null) {
            sub = payload.get("userId");
            if (sub == null) {
                sub = payload.get("user_id");
            }
        }
        if (sub == null) {
            throw new IllegalArgumentException("Token missing subject");
        }
        return UUID.fromString(sub.toString());
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }
}
