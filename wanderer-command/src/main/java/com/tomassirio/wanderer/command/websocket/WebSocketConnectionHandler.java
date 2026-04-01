package com.tomassirio.wanderer.command.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.command.websocket.auth.AuthenticationResult;
import com.tomassirio.wanderer.command.websocket.auth.WebSocketAuthenticationService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketConnectionHandler extends TextWebSocketHandler {

    private final WebSocketAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            AuthenticationResult authResult = authenticationService.authenticate(session);
            
            if (!authResult.isAuthenticated()) {
                log.warn("WebSocket authentication failed: sessionId={}, error={}",
                        session.getId(), authResult.getErrorMessage());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            
            if (authResult.isAnonymous()) {
                log.info("WebSocket connection established (anonymous): sessionId={}, remoteAddress={}",
                        session.getId(), session.getRemoteAddress());
            } else {
                log.info("WebSocket connection established: sessionId={}, userId={}",
                        session.getId(), authResult.getUserId());
            }

            sessionManager.registerSession(session, authResult.getUserId());

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
