package com.tomassirio.wanderer.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripMaintenanceStatsDTO;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.impl.TripServiceImpl;
import com.tomassirio.wanderer.query.utils.TestEntityFactory;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;

    @Mock private FriendshipRepository friendshipRepository;

    @Mock private UserFollowRepository userFollowRepository;

    @Mock private UserRepository userRepository;

    @InjectMocks private TripServiceImpl tripService;

    @Test
    void getTrip_whenTripExists_shouldReturnTripDTO() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findById(TestEntityFactory.USER_ID))
                .thenReturn(Optional.of(TestEntityFactory.createUser()));

        // When
        TripDTO result = tripService.getTrip(tripId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(tripId.toString());
        assertThat(result.name()).isEqualTo("Test Trip");
        assertThat(result.userId()).isEqualTo(TestEntityFactory.USER_ID.toString());
        assertThat(result.tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.tripSettings().tripStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(result.enabled()).isTrue();

        verify(tripRepository).findById(tripId);
    }

    @Test
    void getTrip_whenTripDoesNotExist_shouldThrowEntityNotFoundException() {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();
        when(tripRepository.findById(nonExistentTripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripService.getTrip(nonExistentTripId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip not found");

        verify(tripRepository).findById(nonExistentTripId);
    }

    @Test
    void getAllTrips_whenTripsExist_shouldReturnListOfTripDTOs() {
        // Given
        Trip trip1 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 1", TripVisibility.PUBLIC);
        Trip trip2 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 2", TripVisibility.PRIVATE);

        List<Trip> trips = List.of(trip1, trip2);
        when(tripRepository.findAll()).thenReturn(trips);
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getAllTrips();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().name()).isEqualTo("Trip 1");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.get(0).tripSettings().tripStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(result.get(1).name()).isEqualTo("Trip 2");
        assertThat(result.get(1).tripSettings().visibility()).isEqualTo(TripVisibility.PRIVATE);
        assertThat(result.get(1).tripSettings().tripStatus()).isEqualTo(TripStatus.CREATED);

        verify(tripRepository).findAll();
    }

    @Test
    void getAllTrips_whenNoTripsExist_shouldReturnEmptyList() {
        // Given
        when(tripRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        List<TripDTO> result = tripService.getAllTrips();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(tripRepository).findAll();
    }

    @Test
    void getAllTrips_shouldMapAllFieldsCorrectly() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip trip =
                TestEntityFactory.createTrip(tripId, "Summer Road Trip", TripVisibility.PROTECTED);

        when(tripRepository.findAll()).thenReturn(List.of(trip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getAllTrips();

        // Then
        assertThat(result).hasSize(1);
        TripDTO tripDTO = result.getFirst();
        assertThat(tripDTO.id()).isEqualTo(tripId.toString());
        assertThat(tripDTO.name()).isEqualTo("Summer Road Trip");
        assertThat(tripDTO.userId()).isEqualTo(TestEntityFactory.USER_ID.toString());
        assertThat(tripDTO.tripSettings().visibility()).isEqualTo(TripVisibility.PROTECTED);
        assertThat(tripDTO.tripSettings().tripStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(tripDTO.enabled()).isTrue();
        assertThat(tripDTO.creationTimestamp()).isNotNull();

        verify(tripRepository).findAll();
    }

    @Test
    void getTripsForUser_whenTripsExist_shouldReturnTripDTOs() {
        // Given
        UUID userId = TestEntityFactory.USER_ID;
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Owned Trip");

        when(tripRepository.findByUserId(userId)).thenReturn(List.of(trip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        TripDTO dto = result.getFirst();
        assertThat(dto.id()).isEqualTo(tripId.toString());
        assertThat(dto.userId()).isEqualTo(userId.toString());
        assertThat(dto.name()).isEqualTo("Owned Trip");

        verify(tripRepository).findByUserId(userId);
    }

    @Test
    void getTripsForUser_whenNoTripsExist_shouldReturnEmptyList() {
        // Given
        UUID userId = TestEntityFactory.USER_ID;
        when(tripRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // When
        List<TripDTO> result = tripService.getTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(tripRepository).findByUserId(userId);
    }

    @Test
    void getPublicTrips_whenPublicTripsExist_shouldReturnOnlyPublicTrips() {
        // Given
        Trip publicTrip1 =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Public Trip 1", TripVisibility.PUBLIC);
        Trip publicTrip2 =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Public Trip 2", TripVisibility.PUBLIC);

        when(tripRepository.findByTripSettingsVisibility(TripVisibility.PUBLIC))
                .thenReturn(List.of(publicTrip1, publicTrip2));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getPublicTrips();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Public Trip 1");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.get(1).name()).isEqualTo("Public Trip 2");
        assertThat(result.get(1).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);

        verify(tripRepository).findByTripSettingsVisibility(TripVisibility.PUBLIC);
    }

    @Test
    void getPublicTrips_whenNoPublicTripsExist_shouldReturnEmptyList() {
        // Given
        when(tripRepository.findByTripSettingsVisibility(TripVisibility.PUBLIC))
                .thenReturn(Collections.emptyList());

        // When
        List<TripDTO> result = tripService.getPublicTrips();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(tripRepository).findByTripSettingsVisibility(TripVisibility.PUBLIC);
    }

    @Test
    void getTripsForUserWithVisibility_whenTripsExist_shouldReturnPublicAndProtectedTrips() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        Trip publicTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Public Trip", TripVisibility.PUBLIC);
        Trip protectedTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Protected Trip", TripVisibility.PROTECTED);

        when(friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId))
                .thenReturn(true);
        List<TripVisibility> visibilities =
                List.of(TripVisibility.PUBLIC, TripVisibility.PROTECTED);
        when(tripRepository.findByUserIdAndVisibilityIn(userId, visibilities))
                .thenReturn(List.of(publicTrip, protectedTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getTripsForUserWithVisibility(userId, requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Public Trip");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.get(1).name()).isEqualTo("Protected Trip");
        assertThat(result.get(1).tripSettings().visibility()).isEqualTo(TripVisibility.PROTECTED);

        verify(tripRepository).findByUserIdAndVisibilityIn(userId, visibilities);
    }

    @Test
    void getTripsForUserWithVisibility_whenNoTripsExist_shouldReturnEmptyList() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        when(friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId))
                .thenReturn(true);
        List<TripVisibility> visibilities =
                List.of(TripVisibility.PUBLIC, TripVisibility.PROTECTED);
        when(tripRepository.findByUserIdAndVisibilityIn(userId, visibilities))
                .thenReturn(Collections.emptyList());

        // When
        List<TripDTO> result = tripService.getTripsForUserWithVisibility(userId, requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(tripRepository).findByUserIdAndVisibilityIn(userId, visibilities);
    }

    @Test
    void getTripsForUserWithVisibility_shouldNotIncludePrivateTrips() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        Trip publicTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Public Trip", TripVisibility.PUBLIC);

        when(friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId))
                .thenReturn(true);
        List<TripVisibility> visibilities =
                List.of(TripVisibility.PUBLIC, TripVisibility.PROTECTED);
        when(tripRepository.findByUserIdAndVisibilityIn(userId, visibilities))
                .thenReturn(List.of(publicTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getTripsForUserWithVisibility(userId, requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(
                        result.stream()
                                .noneMatch(
                                        dto ->
                                                dto.tripSettings().visibility()
                                                        == TripVisibility.PRIVATE))
                .isTrue();

        verify(tripRepository).findByUserIdAndVisibilityIn(userId, visibilities);
    }

    @Test
    void getTripsForUserWithVisibility_whenNotFriends_shouldReturnOnlyPublicTrips() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        Trip publicTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Public Trip", TripVisibility.PUBLIC);

        when(friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId))
                .thenReturn(false);
        List<TripVisibility> visibilities = List.of(TripVisibility.PUBLIC);
        when(tripRepository.findByUserIdAndVisibilityIn(userId, visibilities))
                .thenReturn(List.of(publicTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getTripsForUserWithVisibility(userId, requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Public Trip");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);

        verify(friendshipRepository).existsByUserIdAndFriendId(requestingUserId, userId);
        verify(tripRepository).findByUserIdAndVisibilityIn(userId, visibilities);
    }

    @Test
    void getTripsForUserWithVisibility_whenRequestingUserIdIsNull_shouldReturnOnlyPublicTrips() {
        // Given
        UUID userId = UUID.randomUUID();
        Trip publicTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Public Trip", TripVisibility.PUBLIC);

        List<TripVisibility> visibilities = List.of(TripVisibility.PUBLIC);
        when(tripRepository.findByUserIdAndVisibilityIn(userId, visibilities))
                .thenReturn(List.of(publicTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getTripsForUserWithVisibility(userId, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Public Trip");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);

        verify(tripRepository).findByUserIdAndVisibilityIn(userId, visibilities);
    }

    @Test
    void getTripsForUserWithVisibility_whenNotFriendsAndNoPublicTrips_shouldReturnEmptyList() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        when(friendshipRepository.existsByUserIdAndFriendId(requestingUserId, userId))
                .thenReturn(false);
        List<TripVisibility> visibilities = List.of(TripVisibility.PUBLIC);
        when(tripRepository.findByUserIdAndVisibilityIn(userId, visibilities))
                .thenReturn(Collections.emptyList());

        // When
        List<TripDTO> result = tripService.getTripsForUserWithVisibility(userId, requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(tripRepository).findByUserIdAndVisibilityIn(userId, visibilities);
    }

    @Test
    void getOngoingPublicTrips_whenOngoingTripsExist_shouldReturnOngoingPublicTrips() {
        // Given
        Trip ongoingTrip1 =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Ongoing Trip 1", TripVisibility.PUBLIC);
        ongoingTrip1.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip ongoingTrip2 =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Ongoing Trip 2", TripVisibility.PUBLIC);
        ongoingTrip2.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        when(tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses()))
                .thenReturn(List.of(ongoingTrip1, ongoingTrip2));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getOngoingPublicTrips(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Ongoing Trip 1");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.get(0).tripSettings().tripStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(result.get(1).name()).isEqualTo("Ongoing Trip 2");
        assertThat(result.get(1).tripSettings().tripStatus()).isEqualTo(TripStatus.IN_PROGRESS);

        verify(tripRepository)
                .findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses());
    }

    @Test
    void getOngoingPublicTrips_whenNoOngoingTripsExist_shouldReturnEmptyList() {
        // Given
        when(tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses()))
                .thenReturn(Collections.emptyList());

        // When
        List<TripDTO> result = tripService.getOngoingPublicTrips(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(tripRepository)
                .findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses());
    }

    @Test
    void getOngoingPublicTrips_shouldOnlyReturnInProgressPublicTrips() {
        // Given
        Trip ongoingPublicTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Ongoing Public", TripVisibility.PUBLIC);
        ongoingPublicTrip.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        when(tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses()))
                .thenReturn(List.of(ongoingPublicTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getOngoingPublicTrips(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.get(0).tripSettings().tripStatus()).isEqualTo(TripStatus.IN_PROGRESS);

        verify(tripRepository)
                .findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses());
    }

    @Test
    void
            getOngoingPublicTrips_withRequestingUserId_whenUserFollowsSomeUsers_shouldPrioritizeFollowedUsers() {
        // Given
        UUID requestingUserId = UUID.randomUUID();
        UUID followedUserId1 = UUID.randomUUID();
        UUID followedUserId2 = UUID.randomUUID();
        UUID notFollowedUserId = UUID.randomUUID();

        Trip followedTrip1 =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), followedUserId1, "Followed Trip 1");
        followedTrip1.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip followedTrip2 =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), followedUserId2, "Followed Trip 2");
        followedTrip2.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip notFollowedTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), notFollowedUserId, "Not Followed Trip");
        notFollowedTrip.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        UserFollow follow1 =
                UserFollow.builder()
                        .id(UUID.randomUUID())
                        .followerId(requestingUserId)
                        .followedId(followedUserId1)
                        .build();

        UserFollow follow2 =
                UserFollow.builder()
                        .id(UUID.randomUUID())
                        .followerId(requestingUserId)
                        .followedId(followedUserId2)
                        .build();

        when(tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses()))
                .thenReturn(List.of(notFollowedTrip, followedTrip1, followedTrip2));
        when(userFollowRepository.findByFollowerId(requestingUserId))
                .thenReturn(List.of(follow1, follow2));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(
                        List.of(
                                TestEntityFactory.createUser(followedUserId1, "user1"),
                                TestEntityFactory.createUser(followedUserId2, "user2"),
                                TestEntityFactory.createUser(notFollowedUserId, "user3")));

        // When
        List<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        // Followed trips should come first
        assertThat(result.get(0).name()).isEqualTo("Followed Trip 1");
        assertThat(result.get(1).name()).isEqualTo("Followed Trip 2");
        assertThat(result.get(2).name()).isEqualTo("Not Followed Trip");

        verify(tripRepository)
                .findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses());
        verify(userFollowRepository).findByFollowerId(requestingUserId);
    }

    @Test
    void getOngoingPublicTrips_withRequestingUserId_whenUserFollowsNoOne_shouldReturnAllTrips() {
        // Given
        UUID requestingUserId = UUID.randomUUID();

        Trip trip1 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 1", TripVisibility.PUBLIC);
        trip1.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip trip2 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 2", TripVisibility.PUBLIC);
        trip2.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        when(tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses()))
                .thenReturn(List.of(trip1, trip2));
        when(userFollowRepository.findByFollowerId(requestingUserId))
                .thenReturn(Collections.emptyList());
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser()));

        // When
        List<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Trip 1");
        assertThat(result.get(1).name()).isEqualTo("Trip 2");

        verify(tripRepository)
                .findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses());
        verify(userFollowRepository).findByFollowerId(requestingUserId);
    }

    @Test
    void
            getOngoingPublicTrips_withRequestingUserId_whenAllTripsAreFromFollowedUsers_shouldReturnAllInOrder() {
        // Given
        UUID requestingUserId = UUID.randomUUID();
        UUID followedUserId1 = UUID.randomUUID();
        UUID followedUserId2 = UUID.randomUUID();

        Trip followedTrip1 =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), followedUserId1, "Followed Trip 1");
        followedTrip1.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip followedTrip2 =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), followedUserId2, "Followed Trip 2");
        followedTrip2.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        UserFollow follow1 =
                UserFollow.builder()
                        .id(UUID.randomUUID())
                        .followerId(requestingUserId)
                        .followedId(followedUserId1)
                        .build();

        UserFollow follow2 =
                UserFollow.builder()
                        .id(UUID.randomUUID())
                        .followerId(requestingUserId)
                        .followedId(followedUserId2)
                        .build();

        when(tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses()))
                .thenReturn(List.of(followedTrip1, followedTrip2));
        when(userFollowRepository.findByFollowerId(requestingUserId))
                .thenReturn(List.of(follow1, follow2));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(
                        List.of(
                                TestEntityFactory.createUser(followedUserId1, "user1"),
                                TestEntityFactory.createUser(followedUserId2, "user2")));

        // When
        List<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Followed Trip 1");
        assertThat(result.get(1).name()).isEqualTo("Followed Trip 2");

        verify(tripRepository)
                .findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses());
        verify(userFollowRepository).findByFollowerId(requestingUserId);
    }

    @Test
    void
            getOngoingPublicTrips_withRequestingUserId_whenNoTripsFromFollowedUsers_shouldReturnAllTrips() {
        // Given
        UUID requestingUserId = UUID.randomUUID();
        UUID followedUserId = UUID.randomUUID();
        UUID notFollowedUserId1 = UUID.randomUUID();
        UUID notFollowedUserId2 = UUID.randomUUID();

        Trip notFollowedTrip1 =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), notFollowedUserId1, "Not Followed Trip 1");
        notFollowedTrip1.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip notFollowedTrip2 =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), notFollowedUserId2, "Not Followed Trip 2");
        notFollowedTrip2.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        UserFollow follow =
                UserFollow.builder()
                        .id(UUID.randomUUID())
                        .followerId(requestingUserId)
                        .followedId(followedUserId)
                        .build();

        when(tripRepository.findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses()))
                .thenReturn(List.of(notFollowedTrip1, notFollowedTrip2));
        when(userFollowRepository.findByFollowerId(requestingUserId)).thenReturn(List.of(follow));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(
                        List.of(
                                TestEntityFactory.createUser(notFollowedUserId1, "user1"),
                                TestEntityFactory.createUser(notFollowedUserId2, "user2")));

        // When
        List<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Not Followed Trip 1");
        assertThat(result.get(1).name()).isEqualTo("Not Followed Trip 2");

        verify(tripRepository)
                .findByVisibilityAndStatusIn(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses());
        verify(userFollowRepository).findByFollowerId(requestingUserId);
    }

    @Test
    void getAllAvailableTripsForUser_whenUserHasOwnTripsAndPublicTrips_shouldReturnAll() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        UUID publicUserId = UUID.randomUUID();

        Trip ownTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), userId, "My Trip", TripVisibility.PRIVATE);
        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), publicUserId, "Public Trip", TripVisibility.PUBLIC);
        Trip friendTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), friendId, "Friend Trip", TripVisibility.PROTECTED);

        when(friendshipRepository.findByUserId(userId))
                .thenReturn(
                        List.of(
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId)
                                        .build()));
        when(tripRepository.findAllAvailableTripsForUser(userId, List.of(friendId)))
                .thenReturn(List.of(ownTrip, publicTrip, friendTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(
                        List.of(
                                TestEntityFactory.createUser(userId, "myuser"),
                                TestEntityFactory.createUser(publicUserId, "publicuser"),
                                TestEntityFactory.createUser(friendId, "frienduser")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).name()).isEqualTo("My Trip");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PRIVATE);
        assertThat(result.get(1).name()).isEqualTo("Public Trip");
        assertThat(result.get(1).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.get(2).name()).isEqualTo("Friend Trip");
        assertThat(result.get(2).tripSettings().visibility()).isEqualTo(TripVisibility.PROTECTED);

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, List.of(friendId));
    }

    @Test
    void getAllAvailableTripsForUser_whenUserHasNoFriends_shouldReturnOwnAndPublicTrips() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID publicUserId = UUID.randomUUID();

        Trip ownTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), userId, "My Trip", TripVisibility.PRIVATE);
        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), publicUserId, "Public Trip", TripVisibility.PUBLIC);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList()))
                .thenReturn(List.of(ownTrip, publicTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(
                        List.of(
                                TestEntityFactory.createUser(userId, "myuser"),
                                TestEntityFactory.createUser(publicUserId, "publicuser")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("My Trip");
        assertThat(result.get(1).name()).isEqualTo("Public Trip");

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, Collections.emptyList());
    }

    @Test
    void getAllAvailableTripsForUser_whenNoTripsAvailable_shouldReturnEmptyList() {
        // Given
        UUID userId = UUID.randomUUID();

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, Collections.emptyList());
    }

    @Test
    void getAllAvailableTripsForUser_shouldIncludeOwnPrivateTrips() {
        // Given
        UUID userId = UUID.randomUUID();

        Trip privateTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), userId, "My Private Trip", TripVisibility.PRIVATE);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList()))
                .thenReturn(List.of(privateTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser(userId, "myuser")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("My Private Trip");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PRIVATE);

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, Collections.emptyList());
    }

    @Test
    void getAllAvailableTripsForUser_shouldIncludePublicTripsFromOtherUsers() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(),
                        otherUserId,
                        "Other User Public Trip",
                        TripVisibility.PUBLIC);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList()))
                .thenReturn(List.of(publicTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser(otherUserId, "otheruser")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Other User Public Trip");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, Collections.emptyList());
    }

    @Test
    void getAllAvailableTripsForUser_shouldIncludeProtectedTripsFromFriends() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();

        Trip friendProtectedTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(),
                        friendId,
                        "Friend Protected Trip",
                        TripVisibility.PROTECTED);

        when(friendshipRepository.findByUserId(userId))
                .thenReturn(
                        List.of(
                                com.tomassirio.wanderer.commons.domain.Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId)
                                        .build()));
        when(tripRepository.findAllAvailableTripsForUser(userId, List.of(friendId)))
                .thenReturn(List.of(friendProtectedTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser(friendId, "frienduser")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Friend Protected Trip");
        assertThat(result.get(0).tripSettings().visibility()).isEqualTo(TripVisibility.PROTECTED);

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, List.of(friendId));
    }

    @Test
    void getAllAvailableTripsForUser_shouldNotIncludeProtectedTripsFromNonFriends() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID publicUserId = UUID.randomUUID();

        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), publicUserId, "Public Trip", TripVisibility.PUBLIC);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList()))
                .thenReturn(List.of(publicTrip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser(publicUserId, "publicuser")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(
                        result.stream()
                                .noneMatch(
                                        dto ->
                                                dto.tripSettings().visibility()
                                                        == TripVisibility.PROTECTED))
                .isTrue();

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, Collections.emptyList());
    }

    @Test
    void getAllAvailableTripsForUser_withMultipleFriends_shouldReturnAllAvailableTrips() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID friendId1 = UUID.randomUUID();
        UUID friendId2 = UUID.randomUUID();
        UUID publicUserId = UUID.randomUUID();

        Trip ownTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), userId, "My Trip", TripVisibility.PRIVATE);
        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), publicUserId, "Public Trip", TripVisibility.PUBLIC);
        Trip friend1Trip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), friendId1, "Friend 1 Trip", TripVisibility.PROTECTED);
        Trip friend2Trip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), friendId2, "Friend 2 Trip", TripVisibility.PROTECTED);

        when(friendshipRepository.findByUserId(userId))
                .thenReturn(
                        List.of(
                                com.tomassirio.wanderer.commons.domain.Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId1)
                                        .build(),
                                com.tomassirio.wanderer.commons.domain.Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId2)
                                        .build()));
        when(tripRepository.findAllAvailableTripsForUser(userId, List.of(friendId1, friendId2)))
                .thenReturn(List.of(ownTrip, publicTrip, friend1Trip, friend2Trip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(
                        List.of(
                                TestEntityFactory.createUser(userId, "myuser"),
                                TestEntityFactory.createUser(publicUserId, "publicuser"),
                                TestEntityFactory.createUser(friendId1, "friend1"),
                                TestEntityFactory.createUser(friendId2, "friend2")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(4);
        assertThat(result.get(0).name()).isEqualTo("My Trip");
        assertThat(result.get(1).name()).isEqualTo("Public Trip");
        assertThat(result.get(2).name()).isEqualTo("Friend 1 Trip");
        assertThat(result.get(3).name()).isEqualTo("Friend 2 Trip");

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, List.of(friendId1, friendId2));
    }

    @Test
    void getAllAvailableTripsForUser_shouldMapAllFieldsCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        Trip trip =
                TestEntityFactory.createTripWithUser(
                        tripId, userId, "Test Trip", TripVisibility.PUBLIC);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList()))
                .thenReturn(List.of(trip));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(TestEntityFactory.createUser(userId, "testuser")));

        // When
        List<TripDTO> result = tripService.getAllAvailableTripsForUser(userId);

        // Then
        assertThat(result).hasSize(1);
        TripDTO tripDTO = result.getFirst();
        assertThat(tripDTO.id()).isEqualTo(tripId.toString());
        assertThat(tripDTO.name()).isEqualTo("Test Trip");
        assertThat(tripDTO.userId()).isEqualTo(userId.toString());
        assertThat(tripDTO.tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(tripDTO.tripSettings().tripStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(tripDTO.enabled()).isTrue();
        assertThat(tripDTO.creationTimestamp()).isNotNull();

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, Collections.emptyList());
    }

    // ========================================================================
    // getTripMaintenanceStats
    // ========================================================================

    @Test
    void getTripMaintenanceStats_whenNoTrips_shouldReturnAllZeros() {
        // Given
        when(tripRepository.findAll()).thenReturn(List.of());

        // When
        TripMaintenanceStatsDTO stats = tripService.getTripMaintenanceStats();

        // Then
        assertThat(stats.totalTrips()).isZero();
        assertThat(stats.tripsWithPolyline()).isZero();
        assertThat(stats.tripsWithMultipleLocations()).isZero();
        assertThat(stats.tripsMissingPolyline()).isZero();
        assertThat(stats.totalUpdates()).isZero();
        assertThat(stats.updatesWithGeocoding()).isZero();
        assertThat(stats.updatesMissingGeocoding()).isZero();
    }

    @Test
    void getTripMaintenanceStats_withTripsAndUpdates_shouldComputeCorrectStats() {
        // Given
        UUID tripId1 = UUID.randomUUID();
        UUID tripId2 = UUID.randomUUID();

        Trip trip1 = TestEntityFactory.createTrip(tripId1, "Trip 1");
        TripUpdate update1a = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip1);
        TripUpdate update1b = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip1);
        trip1.setTripUpdates(List.of(update1a, update1b));
        trip1.setEncodedPolyline("encodedPolyline");

        Trip trip2 = TestEntityFactory.createTrip(tripId2, "Trip 2");
        TripUpdate update2a = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip2);
        update2a.setCity(null);
        update2a.setCountry(null);
        trip2.setTripUpdates(List.of(update2a));

        when(tripRepository.findAll()).thenReturn(List.of(trip1, trip2));

        // When
        TripMaintenanceStatsDTO stats = tripService.getTripMaintenanceStats();

        // Then
        assertThat(stats.totalTrips()).isEqualTo(2);
        assertThat(stats.tripsWithPolyline()).isEqualTo(1);
        assertThat(stats.tripsWithMultipleLocations()).isEqualTo(1);
        assertThat(stats.tripsMissingPolyline()).isZero();
        assertThat(stats.totalUpdates()).isEqualTo(3);
        assertThat(stats.updatesWithGeocoding()).isEqualTo(2);
        assertThat(stats.updatesMissingGeocoding()).isEqualTo(1);
    }

    @Test
    void getTripMaintenanceStats_tripMissingPolyline_shouldBeCountedCorrectly() {
        // Given — trip with 2+ locations but no polyline
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "No Polyline Trip");
        TripUpdate update1 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        TripUpdate update2 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        trip.setTripUpdates(List.of(update1, update2));
        trip.setEncodedPolyline(null);

        when(tripRepository.findAll()).thenReturn(List.of(trip));

        // When
        TripMaintenanceStatsDTO stats = tripService.getTripMaintenanceStats();

        // Then
        assertThat(stats.totalTrips()).isEqualTo(1);
        assertThat(stats.tripsWithPolyline()).isZero();
        assertThat(stats.tripsWithMultipleLocations()).isEqualTo(1);
        assertThat(stats.tripsMissingPolyline()).isEqualTo(1);
    }

    @Test
    void getTripMaintenanceStats_allUpdatesMissingGeocoding_shouldReflectCorrectly() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "No Geocoding Trip");
        TripUpdate update1 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        update1.setCity(null);
        update1.setCountry(null);
        TripUpdate update2 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        update2.setCity(null);
        update2.setCountry(null);
        trip.setTripUpdates(List.of(update1, update2));

        when(tripRepository.findAll()).thenReturn(List.of(trip));

        // When
        TripMaintenanceStatsDTO stats = tripService.getTripMaintenanceStats();

        // Then
        assertThat(stats.totalUpdates()).isEqualTo(2);
        assertThat(stats.updatesWithGeocoding()).isZero();
        assertThat(stats.updatesMissingGeocoding()).isEqualTo(2);
    }
}
