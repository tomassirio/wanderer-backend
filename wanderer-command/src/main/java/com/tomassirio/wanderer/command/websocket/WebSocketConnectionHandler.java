package com.tomassirio.wanderer.command.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.commons.security.JwtUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketConnectionHandler extends TextWebSocketHandler {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            // Extract and validate token from query parameters
            String token = extractToken(session);
            UUID userId = validateToken(token);

            // Register the session
            sessionManager.registerSession(session, userId);

            log.info(
                    "WebSocket connection established: sessionId={}, userId={}",
                    session.getId(),
                    userId);
        } catch (ResponseStatusException e) {
            log.warn("WebSocket authentication failed: {}", e.getReason());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ex) {
                log.error("Error closing WebSocket session", ex);
            }
        } catch (Exception e) {
            log.error("Error during WebSocket connection establishment", e);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ex) {
                log.error("Error closing WebSocket session", ex);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.debug(
                    "Received WebSocket message: sessionId={}, message={}",
                    session.getId(),
                    payload);

            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);

            switch (wsMessage.getType()) {
                case "SUBSCRIBE" -> handleSubscribe(session, wsMessage.getDestination());
                case "UNSUBSCRIBE" -> handleUnsubscribe(session, wsMessage.getDestination());
                case "PING" -> handlePing(session);
                default ->
                        log.warn(
                                "Unknown WebSocket message type: {} from session {}",
                                wsMessage.getType(),
                                session.getId());
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message from session {}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.unregisterSession(session);
        log.info("WebSocket connection closed: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        if (exception instanceof java.io.EOFException) {
            log.warn("WebSocket client disconnected abruptly: sessionId={}", session.getId());
        } else {
            log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
        }
        sessionManager.unregisterSession(session);
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";

        if (query == null || query.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing token query parameter");
        }

        Map<String, String> params = parseQueryParams(query);
        String token = params.get("token");

        if (token == null || token.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing token query parameter");
        }

        return token;
    }

    private UUID validateToken(String token) {
        try {
            Map<String, Object> payload = jwtUtils.parsePayload(token);
            Object sub = payload.get("sub");
            if (sub == null) {
                sub = payload.get("userId");
                if (sub == null) sub = payload.get("user_id");
            }
            if (sub == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing subject");
            }
            return UUID.fromString(sub.toString());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token", e);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }

    private void handleSubscribe(WebSocketSession session, String destination) {
        if (destination == null
                || (!destination.startsWith("/topic/trips/")
                        && !destination.startsWith("/topic/users/"))) {
            log.warn(
                    "Invalid subscription destination: {} from session {}",
                    destination,
                    session.getId());
            return;
        }

        sessionManager.subscribe(session, destination);
        log.info("Session {} subscribed to {}", session.getId(), destination);
    }

    private void handleUnsubscribe(WebSocketSession session, String destination) {
        if (destination == null) {
            log.warn("Null unsubscribe destination from session {}", session.getId());
            return;
        }

        sessionManager.unsubscribe(session, destination);
        log.info("Session {} unsubscribed from {}", session.getId(), destination);
    }

    private void handlePing(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage("PONG"));
            log.debug("Sent PONG to session {}", session.getId());
        } catch (IOException e) {
            log.error("Error sending PONG to session {}", session.getId(), e);
        }
    }
}
