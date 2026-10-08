package com.tomassirio.wanderer.query.service.helper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TripVisibilityHelperTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Mock private TripRepository tripRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @InjectMocks private TripVisibilityHelper helper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0} viewing {1} trip -> allowed={2}")
    @CsvSource({
        "OWNER, PUBLIC, true",
        "OWNER, PROTECTED, true",
        "OWNER, PRIVATE, true",
        "FRIEND, PUBLIC, true",
        "FRIEND, PROTECTED, true",
        "FRIEND, PRIVATE, false",
        "STRANGER, PUBLIC, true",
        "STRANGER, PROTECTED, false",
        "STRANGER, PRIVATE, false",
        "ANONYMOUS, PUBLIC, true",
        "ANONYMOUS, PROTECTED, false",
        "ANONYMOUS, PRIVATE, false"
    })
    void assertCanView_appliesVisibilityRules(
            String requester, TripVisibility visibility, boolean allowed) {
        UUID tripId = givenTrip(visibility);
        lenient()
                .when(friendshipRepository.existsByUserIdAndFriendId(any(), any()))
                .thenReturn(false);
        lenient()
                .when(friendshipRepository.existsByUserIdAndFriendId(FRIEND, OWNER))
                .thenReturn(true);
        UUID requesterId =
                switch (requester) {
                    case "OWNER" -> OWNER;
                    case "FRIEND" -> FRIEND;
                    case "STRANGER" -> STRANGER;
                    default -> null;
                };

        if (allowed) {
            assertThatCode(() -> helper.assertCanView(tripId, requesterId))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> helper.assertCanView(tripId, requesterId))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void assertCanView_adminSeesPrivateTrip() {
        UUID tripId = givenTrip(TripVisibility.PRIVATE);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken(
                                STRANGER, null, List.of(() -> "ROLE_ADMIN")));

        assertThatCode(() -> helper.assertCanView(tripId, STRANGER)).doesNotThrowAnyException();
    }

    @Test
    void assertCanView_unknownTrip_throwsNotFound() {
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> helper.assertCanView(tripId, OWNER))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private UUID givenTrip(TripVisibility visibility) {
        Trip trip =
                Trip.builder()
                        .id(UUID.randomUUID())
                        .userId(OWNER)
                        .tripSettings(TripSettings.builder().visibility(visibility).build())
                        .build();
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        return trip.getId();
    }
}
