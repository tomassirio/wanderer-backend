package com.tomassirio.wanderer.command.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class WebSocketSessionManager {

    // sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // sessionId -> userId (null not allowed in ConcurrentHashMap, so we use Optional)
    private final Map<String, UUID> sessionUsers = new ConcurrentHashMap<>();
    
    // sessionId -> anonymous flag (since ConcurrentHashMap doesn't support null values)
    private final Map<String, Boolean> sessionAnonymousFlags = new ConcurrentHashMap<>();

    // topic -> set of sessionIds
    private final Map<String, Set<String>> topicSubscriptions = new ConcurrentHashMap<>();

    // sessionId -> subscription count (for rate limiting anonymous users)
    private final Map<String, Integer> sessionSubscriptionCounts = new ConcurrentHashMap<>();

    // Maximum subscriptions per anonymous session
    private static final int MAX_ANONYMOUS_SUBSCRIPTIONS = 10;

    public void registerSession(WebSocketSession session, UUID userId) {
        sessions.put(session.getId(), session);
        
        // ConcurrentHashMap doesn't allow null values, so track anonymous status separately
        boolean isAnonymous = (userId == null);
        if (!isAnonymous) {
            sessionUsers.put(session.getId(), userId);
        }
        sessionAnonymousFlags.put(session.getId(), isAnonymous);
        sessionSubscriptionCounts.put(session.getId(), 0);
        
        log.info(
                "Registered session: {} for user: {} (anonymous: {})",
                session.getId(),
                userId,
                isAnonymous);
    }

    public void unregisterSession(WebSocketSession session) {
        String sessionId = session.getId();

        // Remove from all topic subscriptions
        topicSubscriptions.values().forEach(subscribers -> subscribers.remove(sessionId));

        sessions.remove(sessionId);
        sessionUsers.remove(sessionId);
        sessionAnonymousFlags.remove(sessionId);
        sessionSubscriptionCounts.remove(sessionId);

        log.info("Unregistered session: {}", sessionId);
    }

    public void subscribe(WebSocketSession session, String topic) {
        String sessionId = session.getId();
        Boolean isAnonymous = sessionAnonymousFlags.getOrDefault(sessionId, false);

        // Rate limit anonymous users
        if (isAnonymous) {
            Integer currentCount = sessionSubscriptionCounts.getOrDefault(sessionId, 0);
            if (currentCount >= MAX_ANONYMOUS_SUBSCRIPTIONS) {
                log.warn(
                        "Anonymous session {} exceeded subscription limit ({}), rejecting subscription to {}",
                        sessionId,
                        MAX_ANONYMOUS_SUBSCRIPTIONS,
                        topic);
                return;
            }
            sessionSubscriptionCounts.put(sessionId, currentCount + 1);
        }

        topicSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        log.debug(
                "Session {} subscribed to topic {} (anonymous: {})",
                sessionId,
                topic,
                isAnonymous);
    }

    public void unsubscribe(WebSocketSession session, String topic) {
        String sessionId = session.getId();
        Boolean isAnonymous = sessionAnonymousFlags.getOrDefault(sessionId, false);

        // Decrement subscription count for anonymous users
        if (isAnonymous) {
            Integer currentCount = sessionSubscriptionCounts.getOrDefault(sessionId, 0);
            if (currentCount > 0) {
                sessionSubscriptionCounts.put(sessionId, currentCount - 1);
            }
        }
        Set<String> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            subscribers.remove(sessionId);
            log.debug("Session {} unsubscribed from topic {}", sessionId, topic);
        }
    }

    public void broadcast(String topic, String message) {
        Set<String> subscribers = topicSubscriptions.get(topic);
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for topic: {}", topic);
            return;
        }

        log.debug("Broadcasting to {} subscribers on topic: {}", subscribers.size(), topic);

        for (String sessionId : subscribers) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                    log.debug("Sent message to session: {}", sessionId);
                } catch (IOException e) {
                    log.error("Error sending message to session: {}", sessionId, e);
                }
            } else {
                log.warn("Session {} is not open, removing from subscriptions", sessionId);
                subscribers.remove(sessionId);
            }
        }
    }

    public UUID getUserId(WebSocketSession session) {
        return sessionUsers.get(session.getId());
    }

    public int getActiveSessionsCount() {
        return sessions.size();
    }

    public int getSubscribersCount(String topic) {
        Set<String> subscribers = topicSubscriptions.get(topic);
        return subscribers != null ? subscribers.size() : 0;
    }
}
