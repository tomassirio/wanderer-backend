package com.tomassirio.wanderer.command.websocket;

import com.tomassirio.wanderer.command.repository.FriendshipRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether a websocket session may subscribe to a trip topic ({@code /topic/trips/{id}}).
 *
 * <p>Same rule as reading the trip over REST: PUBLIC for everyone, PROTECTED for the owner and
 * friends, PRIVATE for the owner only. Trip topics carry live locations, so an unknown trip or a
 * malformed id is denied.
 */
@Component
@RequiredArgsConstructor
public class TripTopicAuthorizer {

    private static final String TRIP_TOPIC_PREFIX = "/topic/trips/";

    private final TripRepository tripRepository;
    private final FriendshipRepository friendshipRepository;

    public static boolean isTripTopic(String destination) {
        return destination.startsWith(TRIP_TOPIC_PREFIX);
    }

    // ponytail: checked once at subscribe time; a trip made private later keeps its current
    // subscribers until they reconnect. Re-check on visibility change if that matters.
    @Transactional(readOnly = true)
    public boolean canSubscribe(String destination, UUID userId) {
        UUID tripId;
        try {
            tripId = UUID.fromString(destination.substring(TRIP_TOPIC_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }

        return tripRepository
                .findById(tripId)
                .map(
                        trip -> {
                            TripVisibility visibility =
                                    trip.getTripSettings() != null
                                            ? trip.getTripSettings().getVisibility()
                                            : null;
                            if (visibility == TripVisibility.PUBLIC
                                    || trip.getUserId().equals(userId)) {
                                return true;
                            }
                            return visibility == TripVisibility.PROTECTED
                                    && userId != null
                                    && friendshipRepository.existsByUserIdAndFriendId(
                                            userId, trip.getUserId());
                        })
                .orElse(false);
    }
}
