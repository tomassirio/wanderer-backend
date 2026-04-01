package com.tomassirio.wanderer.command.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.tomassirio.wanderer.command.websocket.event.WebSocketEventType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebSocketEventTypeTest {

    @Test
    void tripTopic_shouldReturnCorrectTopicFormat() {
        // Given
        UUID tripId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        // When
        String topic = WebSocketEventType.tripTopic(tripId);

        // Then
        assertThat(topic).isEqualTo("/topic/trips/123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void userTopic_shouldReturnCorrectTopicFormat() {
        // Given
        UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        // When
        String topic = WebSocketEventType.userTopic(userId);

        // Then
        assertThat(topic).isEqualTo("/topic/users/123e4567-e89b-12d3-a456-426614174001");
    }

    @Test
    void eventTypeConstants_shouldBeCorrectlyDefined() {
        // Trip lifecycle events
        assertThat(WebSocketEventType.TRIP_CREATED).isEqualTo("TRIP_CREATED");
        assertThat(WebSocketEventType.TRIP_DELETED).isEqualTo("TRIP_DELETED");
        assertThat(WebSocketEventType.TRIP_METADATA_UPDATED).isEqualTo("TRIP_METADATA_UPDATED");
        assertThat(WebSocketEventType.TRIP_STATUS_CHANGED).isEqualTo("TRIP_STATUS_CHANGED");
        assertThat(WebSocketEventType.TRIP_VISIBILITY_CHANGED).isEqualTo("TRIP_VISIBILITY_CHANGED");
        assertThat(WebSocketEventType.TRIP_UPDATED).isEqualTo("TRIP_UPDATED");

        // Comment events
        assertThat(WebSocketEventType.COMMENT_ADDED).isEqualTo("COMMENT_ADDED");
        assertThat(WebSocketEventType.COMMENT_REACTION_ADDED).isEqualTo("COMMENT_REACTION_ADDED");
        assertThat(WebSocketEventType.COMMENT_REACTION_REMOVED)
                .isEqualTo("COMMENT_REACTION_REMOVED");

        // Friend request events
        assertThat(WebSocketEventType.FRIEND_REQUEST_SENT).isEqualTo("FRIEND_REQUEST_SENT");
        assertThat(WebSocketEventType.FRIEND_REQUEST_RECEIVED).isEqualTo("FRIEND_REQUEST_RECEIVED");
        assertThat(WebSocketEventType.FRIEND_REQUEST_ACCEPTED).isEqualTo("FRIEND_REQUEST_ACCEPTED");
        assertThat(WebSocketEventType.FRIEND_REQUEST_DECLINED).isEqualTo("FRIEND_REQUEST_DECLINED");

        // User follow events
        assertThat(WebSocketEventType.USER_FOLLOWED).isEqualTo("USER_FOLLOWED");
        assertThat(WebSocketEventType.USER_UNFOLLOWED).isEqualTo("USER_UNFOLLOWED");
    }
}
