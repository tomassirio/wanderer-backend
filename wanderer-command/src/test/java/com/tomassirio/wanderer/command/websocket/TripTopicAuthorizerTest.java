package com.tomassirio.wanderer.command.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.repository.FriendshipRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripTopicAuthorizerTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final String TOPIC = "/topic/trips/" + TRIP_ID;

    @Mock private TripRepository tripRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @InjectMocks private TripTopicAuthorizer authorizer;

    private void givenTrip(TripVisibility visibility) {
        Trip trip = Trip.builder().id(TRIP_ID).userId(OWNER).build();
        trip.setTripSettings(TripSettings.builder().visibility(visibility).build());
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    }

    @Test
    void publicTrip_anyoneIncludingAnonymous() {
        givenTrip(TripVisibility.PUBLIC);
        assertThat(authorizer.canSubscribe(TOPIC, null)).isTrue();
    }

    @Test
    void privateTrip_ownerOnly() {
        givenTrip(TripVisibility.PRIVATE);
        assertThat(authorizer.canSubscribe(TOPIC, OWNER)).isTrue();
        assertThat(authorizer.canSubscribe(TOPIC, OTHER)).isFalse();
        assertThat(authorizer.canSubscribe(TOPIC, null)).isFalse();
    }

    @Test
    void protectedTrip_friendsAllowed_strangersAndAnonymousDenied() {
        givenTrip(TripVisibility.PROTECTED);
        UUID friend = UUID.randomUUID();
        when(friendshipRepository.existsByUserIdAndFriendId(friend, OWNER)).thenReturn(true);
        when(friendshipRepository.existsByUserIdAndFriendId(OTHER, OWNER)).thenReturn(false);

        assertThat(authorizer.canSubscribe(TOPIC, friend)).isTrue();
        assertThat(authorizer.canSubscribe(TOPIC, OTHER)).isFalse();
        assertThat(authorizer.canSubscribe(TOPIC, null)).isFalse();
    }

    @Test
    void unknownTripOrMalformedId_denied() {
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
        assertThat(authorizer.canSubscribe(TOPIC, OWNER)).isFalse();
        assertThat(authorizer.canSubscribe("/topic/trips/not-a-uuid", OWNER)).isFalse();
    }

    @Test
    void isTripTopic() {
        assertThat(TripTopicAuthorizer.isTripTopic(TOPIC)).isTrue();
        assertThat(TripTopicAuthorizer.isTripTopic("/topic/users/" + OWNER)).isFalse();
    }
}
