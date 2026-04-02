package com.tomassirio.wanderer.command.websocket;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.command.event.CommentAddedEvent;
import com.tomassirio.wanderer.command.event.CommentReactionEvent;
import com.tomassirio.wanderer.command.event.FriendRequestSentEvent;
import com.tomassirio.wanderer.command.event.PolylineUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.event.UserFollowedEvent;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebSocketEventServiceTest {

    @Mock private RedisWebSocketBroadcaster redisBroadcaster;

    private WebSocketEventService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new WebSocketEventService(redisBroadcaster, objectMapper);
    }

    @Test
    void broadcast_withTripUpdatedEvent_shouldBroadcastToTripTopic() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripUpdateId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(40.7128).lon(-74.0060).build();

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(85)
                        .message("Test message")
                        .timestamp(Instant.now())
                        .build();

        // When
        service.broadcast(event);

        // Then
        verify(redisBroadcaster).publishToRedis(eq("/topic/trips/" + tripId), anyString());
    }

    @Test
    void broadcast_withCommentAddedEvent_shouldBroadcastToTripTopic() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentAddedEvent event =
                CommentAddedEvent.builder()
                        .commentId(commentId)
                        .tripId(tripId)
                        .userId(userId)
                        .username("testuser")
                        .message("Test comment")
                        .timestamp(Instant.now())
                        .build();

        // When
        service.broadcast(event);

        // Then
        verify(redisBroadcaster).publishToRedis(eq("/topic/trips/" + tripId), anyString());
    }

    @Test
    void broadcast_withCommentReactionAddedEvent_shouldBroadcastToTripTopic() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentReactionEvent event =
                CommentReactionEvent.builder()
                        .tripId(tripId)
                        .commentId(commentId)
                        .reactionType("HEART")
                        .userId(userId)
                        .added(true)
                        .build();

        // When
        service.broadcast(event);

        // Then
        verify(redisBroadcaster).publishToRedis(eq("/topic/trips/" + tripId), anyString());
    }

    @Test
    void broadcast_withCommentReactionRemovedEvent_shouldBroadcastToTripTopic() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentReactionEvent event =
                CommentReactionEvent.builder()
                        .tripId(tripId)
                        .commentId(commentId)
                        .reactionType("HEART")
                        .userId(userId)
                        .added(false)
                        .build();

        // When
        service.broadcast(event);

        // Then
        verify(redisBroadcaster).publishToRedis(eq("/topic/trips/" + tripId), anyString());
    }

    @Test
    void broadcast_withFriendRequestSentEvent_shouldBroadcastToUserTopic() {
        // Given
        UUID requestId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        FriendRequestSentEvent event =
                FriendRequestSentEvent.builder()
                        .requestId(requestId)
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .status("PENDING")
                        .createdAt(Instant.now())
                        .build();

        // When
        service.broadcast(event);

        // Then
        verify(redisBroadcaster).publishToRedis(eq("/topic/users/" + receiverId), anyString());
    }

    @Test
    void broadcast_withUserFollowedEvent_shouldBroadcastToUserTopic() {
        // Given
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();

        UserFollowedEvent event =
                UserFollowedEvent.builder()
                        .followId(followId)
                        .followerId(followerId)
                        .followedId(followedId)
                        .createdAt(Instant.now())
                        .build();

        // When
        service.broadcast(event);

        // Then
        verify(redisBroadcaster).publishToRedis(eq("/topic/users/" + followedId), anyString());
    }

    @Test
    void broadcast_withPolylineUpdatedEvent_shouldBroadcastToTripTopic() {
        // Given
        UUID tripId = UUID.randomUUID();

        PolylineUpdatedEvent event =
                PolylineUpdatedEvent.builder()
                        .tripId(tripId)
                        .encodedPolyline("encodedPolylineString")
                        .build();

        // When
        service.broadcast(event);

        // Then
        verify(redisBroadcaster).publishToRedis(eq("/topic/trips/" + tripId), anyString());
    }
}
