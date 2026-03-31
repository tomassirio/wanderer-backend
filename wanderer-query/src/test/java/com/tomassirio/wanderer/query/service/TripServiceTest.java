package com.tomassirio.wanderer.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.PromotedTrip;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripMaintenanceStatsDTO;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.PromotedTripRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.TripUpdateRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.helper.TripEnrichmentHelper;
import com.tomassirio.wanderer.commons.mapper.TripMapper;
import com.tomassirio.wanderer.query.service.impl.TripServiceImpl;
import com.tomassirio.wanderer.query.utils.TestEntityFactory;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;

    @Mock private FriendshipRepository friendshipRepository;

    @Mock private UserFollowRepository userFollowRepository;

    @Mock private UserRepository userRepository;

    @Mock private PromotedTripRepository promotedTripRepository;

    @Mock private TripEnrichmentHelper tripEnrichmentHelper;
    
    @Mock private TripUpdateRepository tripUpdateRepository;

    @InjectMocks private TripServiceImpl tripService;
    
    private final TripMapper tripMapper = TripMapper.INSTANCE;
    
    @BeforeEach
    void setUp() {
        // Configure enrichment helper to return enriched DTOs
        // Use lenient() to avoid UnnecessaryStubbingException for tests that don't use all stubs
        
        // enrichWithUsernameAndPromotedStatus
        lenient().when(tripEnrichmentHelper.enrichWithUsernameAndPromotedStatus(any(TripDTO.class)))
                .thenAnswer(invocation -> {
                    TripDTO input = invocation.getArgument(0);
                    // Ensure Boolean fields are non-null
                    return new TripDTO(
                            input.id(),
                            input.name(),
                            input.userId(),
                            input.username(),
                            input.tripSettings(),
                            input.tripDetails(),
                            input.tripPlanId(),
                            input.comments(),
                            input.tripUpdates(),
                            input.tripDays(),
                            input.encodedPolyline(),
                            input.plannedPolyline(),
                            input.polylineUpdatedAt(),
                            input.accruedDistanceKm(),
                            input.creationTimestamp(),
                            input.enabled(),
                            input.isPromoted() != null ? input.isPromoted() : Boolean.FALSE,
                            input.promotedAt(),
                            input.isPreAnnounced() != null ? input.isPreAnnounced() : Boolean.FALSE,
                            input.countdownStartDate(),
                            input.commentsCount(),
                            input.updateCount()
                    );
                });
        
        // enrichListWithUsernames
        lenient().when(tripEnrichmentHelper.enrichListWithUsernames(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // enrichListWithUsernamesAndPromotedStatus
        lenient().when(tripEnrichmentHelper.enrichListWithUsernamesAndPromotedStatus(any(List.class)))
                .thenAnswer(invocation -> {
                    List<TripDTO> inputs = invocation.getArgument(0);
                    // Ensure Boolean fields are non-null
                    return inputs.stream().map(input -> new TripDTO(
                            input.id(),
                            input.name(),
                            input.userId(),
                            input.username(),
                            input.tripSettings(),
                            input.tripDetails(),
                            input.tripPlanId(),
                            input.comments(),
                            input.tripUpdates(),
                            input.tripDays(),
                            input.encodedPolyline(),
                            input.plannedPolyline(),
                            input.polylineUpdatedAt(),
                            input.accruedDistanceKm(),
                            input.creationTimestamp(),
                            input.enabled(),
                            input.isPromoted() != null ? input.isPromoted() : Boolean.FALSE,
                            input.promotedAt(),
                            input.isPreAnnounced() != null ? input.isPreAnnounced() : Boolean.FALSE,
                            input.countdownStartDate(),
                            input.commentsCount(),
                            input.updateCount()
                    )).toList();
                });
        
        // enrichTripsToTripDTOs - used for Page<Trip> -> Page<TripDTO>
        lenient().when(tripEnrichmentHelper.enrichTripsToTripDTOs(any(Page.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Page<Trip> tripPage = invocation.getArgument(0);
                    Pageable pageable = invocation.getArgument(1);
                    List<TripDTO> dtos = tripPage.getContent().stream()
                            .map(tripMapper::toDTO)
                            .map(dto -> new TripDTO(
                                    dto.id(),
                                    dto.name(),
                                    dto.userId(),
                                    dto.username(),
                                    dto.tripSettings(),
                                    dto.tripDetails(),
                                    dto.tripPlanId(),
                                    dto.comments(),
                                    dto.tripUpdates(),
                                    dto.tripDays(),
                                    dto.encodedPolyline(),
                                    dto.plannedPolyline(),
                                    dto.polylineUpdatedAt(),
                                    dto.accruedDistanceKm(),
                                    dto.creationTimestamp(),
                                    dto.enabled(),
                                    dto.isPromoted() != null ? dto.isPromoted() : Boolean.FALSE,
                                    dto.promotedAt(),
                                    dto.isPreAnnounced() != null ? dto.isPreAnnounced() : Boolean.FALSE,
                                    dto.countdownStartDate(),
                                    dto.commentsCount(),
                                    dto.updateCount()
                            ))
                            .toList();
                    return new PageImpl<>(dtos, pageable, tripPage.getTotalElements());
                });
        
        // enrichTripsToSummaryPage
        lenient().when(tripEnrichmentHelper.enrichTripsToSummaryPage(any(Page.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Page<Trip> tripPage = invocation.getArgument(0);
                    Pageable pageable = invocation.getArgument(1);
                    // For tests, we can return an empty result or mock the summary conversion
                    return Page.empty(pageable);
                });
    }

    @Test
    void getTrip_whenTripExists_shouldReturnTripDTO() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");

        when(tripRepository.findWithDetailsById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of());

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

        verify(tripRepository).findWithDetailsById(tripId);
    }

    @Test
    void getTrip_whenTripDoesNotExist_shouldThrowEntityNotFoundException() {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();
        when(tripRepository.findWithDetailsById(nonExistentTripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripService.getTrip(nonExistentTripId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip not found");

        verify(tripRepository).findWithDetailsById(nonExistentTripId);
    }

    @Test
    void getAllTrips_whenTripsExist_shouldReturnPageOfTripDTOs() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        Trip trip1 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 1", TripVisibility.PUBLIC);
        Trip trip2 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 2", TripVisibility.PRIVATE);

        List<Trip> trips = List.of(trip1, trip2);
        when(tripRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(trips, pageable, trips.size()));
        when(tripUpdateRepository.findByTripIdIn(any())).thenReturn(Collections.emptyList());

        // When
        Page<TripDTO> result = tripService.getAllTrips(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().getFirst().name()).isEqualTo("Trip 1");
        assertThat(result.getContent().get(0).tripSettings().visibility())
                .isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.getContent().get(0).tripSettings().tripStatus())
                .isEqualTo(TripStatus.CREATED);
        assertThat(result.getContent().get(1).name()).isEqualTo("Trip 2");
        assertThat(result.getContent().get(1).tripSettings().visibility())
                .isEqualTo(TripVisibility.PRIVATE);

        verify(tripRepository).findAll(pageable);
    }

    @Test
    void getAllTrips_whenNoTripsExist_shouldReturnEmptyPage() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        when(tripRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        // When
        Page<TripDTO> result = tripService.getAllTrips(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(tripRepository).findAll(pageable);
    }

    @Test
    void getAllTrips_shouldMapAllFieldsCorrectly() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID tripId = UUID.randomUUID();

        Trip trip =
                TestEntityFactory.createTrip(tripId, "Summer Road Trip", TripVisibility.PROTECTED);

        when(tripRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(trip), pageable, 1));
        when(tripUpdateRepository.findByTripIdIn(any())).thenReturn(Collections.emptyList());

        // When
        Page<TripDTO> result = tripService.getAllTrips(pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        TripDTO tripDTO = result.getContent().getFirst();
        assertThat(tripDTO.id()).isEqualTo(tripId.toString());
        assertThat(tripDTO.name()).isEqualTo("Summer Road Trip");
        assertThat(tripDTO.userId()).isEqualTo(TestEntityFactory.USER_ID.toString());
        assertThat(tripDTO.tripSettings().visibility()).isEqualTo(TripVisibility.PROTECTED);
        assertThat(tripDTO.tripSettings().tripStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(tripDTO.enabled()).isTrue();
        assertThat(tripDTO.creationTimestamp()).isNotNull();

        verify(tripRepository).findAll(pageable);
    }

    @Test
    void getTripsForUser_whenTripsExist_shouldReturnTripDTOs() {
        // Given
        UUID userId = TestEntityFactory.USER_ID;
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Owned Trip");

        when(tripRepository.findByUserId(userId)).thenReturn(List.of(trip));

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
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        Trip ongoingTrip1 =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Ongoing Trip 1", TripVisibility.PUBLIC);
        ongoingTrip1.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip ongoingTrip2 =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Ongoing Trip 2", TripVisibility.PUBLIC);
        ongoingTrip2.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        List<Trip> trips = List.of(ongoingTrip1, ongoingTrip2);
        when(tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable))
                .thenReturn(new PageImpl<>(trips, pageable, trips.size()));

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("Ongoing Trip 1");
        assertThat(result.getContent().get(0).tripSettings().visibility())
                .isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.getContent().get(0).tripSettings().tripStatus())
                .isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(result.getContent().get(1).name()).isEqualTo("Ongoing Trip 2");
        assertThat(result.getContent().get(1).tripSettings().tripStatus())
                .isEqualTo(TripStatus.IN_PROGRESS);

        verify(tripRepository)
                .findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
    }

    @Test
    void getOngoingPublicTrips_whenNoOngoingTripsExist_shouldReturnEmptyPage() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        when(tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(tripRepository)
                .findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
    }

    @Test
    void getOngoingPublicTrips_shouldOnlyReturnInProgressPublicTrips() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        Trip ongoingPublicTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Ongoing Public", TripVisibility.PUBLIC);
        ongoingPublicTrip.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        when(tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable))
                .thenReturn(new PageImpl<>(List.of(ongoingPublicTrip), pageable, 1));

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().tripSettings().visibility())
                .isEqualTo(TripVisibility.PUBLIC);
        assertThat(result.getContent().getFirst().tripSettings().tripStatus())
                .isEqualTo(TripStatus.IN_PROGRESS);

        verify(tripRepository)
                .findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
    }

    @Test
    void getOngoingPublicTrips_whenPromotedCreatedTripExists_shouldReturnIt() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));

        UUID promotedCreatedTripId = UUID.randomUUID();
        Trip promotedCreatedTrip =
                TestEntityFactory.createTrip(
                        promotedCreatedTripId, "Promoted Coming Soon", TripVisibility.PUBLIC);
        promotedCreatedTrip.getTripSettings().setTripStatus(TripStatus.CREATED);

        Trip activeTrip =
                TestEntityFactory.createTrip(
                        UUID.randomUUID(), "Active Trip", TripVisibility.PUBLIC);
        activeTrip.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        // Promoted CREATED trip should be included in results
        List<Trip> trips = List.of(promotedCreatedTrip, activeTrip);
        when(tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable))
                .thenReturn(new PageImpl<>(trips, pageable, trips.size()));
        
        // Override enrichment for this test to mark the promoted trip
        when(tripEnrichmentHelper.enrichTripsToTripDTOs(any(Page.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Page<Trip> tripPage = invocation.getArgument(0);
                    Pageable page = invocation.getArgument(1);
                    List<TripDTO> dtos = tripPage.getContent().stream()
                            .map(tripMapper::toDTO)
                            .map(dto -> new TripDTO(
                                    dto.id(),
                                    dto.name(),
                                    dto.userId(),
                                    dto.username(),
                                    dto.tripSettings(),
                                    dto.tripDetails(),
                                    dto.tripPlanId(),
                                    dto.comments(),
                                    dto.tripUpdates(),
                                    dto.tripDays(),
                                    dto.encodedPolyline(),
                                    dto.plannedPolyline(),
                                    dto.polylineUpdatedAt(),
                                    dto.accruedDistanceKm(),
                                    dto.creationTimestamp(),
                                    dto.enabled(),
                                    dto.id().equals(promotedCreatedTripId.toString()) ? Boolean.TRUE : Boolean.FALSE,
                                    dto.promotedAt(),
                                    dto.isPreAnnounced() != null ? dto.isPreAnnounced() : Boolean.FALSE,
                                    dto.countdownStartDate(),
                                    dto.commentsCount(),
                                    dto.updateCount()
                            ))
                            .toList();
                    return new PageImpl<>(dtos, page, tripPage.getTotalElements());
                });

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(null, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        // Promoted CREATED trip should be included
        assertThat(
                        result.getContent().stream()
                                .anyMatch(
                                        t ->
                                                t.id().equals(promotedCreatedTripId.toString())
                                                        && t.tripSettings().tripStatus()
                                                                == TripStatus.CREATED
                                                        && t.isPromoted()))
                .isTrue();
        // Regular active trip should also be there
        assertThat(
                        result.getContent().stream()
                                .anyMatch(
                                        t ->
                                                t.name().equals("Active Trip")
                                                        && t.tripSettings().tripStatus()
                                                                == TripStatus.IN_PROGRESS))
                .isTrue();

        verify(tripRepository)
                .findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
    }

    @Test
    void
            getOngoingPublicTrips_withRequestingUserId_whenUserFollowsSomeUsers_shouldPrioritizeFollowedUsers() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
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

        List<Trip> trips = List.of(followedTrip1, followedTrip2, notFollowedTrip);
        when(userFollowRepository.findByFollowerId(requestingUserId))
                .thenReturn(List.of(follow1, follow2));
        when(tripRepository.findPublicActiveTripsWithFollowedPriority(
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(trips, PageRequest.of(0, 20), trips.size()));

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).name()).isEqualTo("Followed Trip 1");
        assertThat(result.getContent().get(1).name()).isEqualTo("Followed Trip 2");
        assertThat(result.getContent().get(2).name()).isEqualTo("Not Followed Trip");
        verify(userFollowRepository).findByFollowerId(requestingUserId);
        verify(tripRepository)
                .findPublicActiveTripsWithFollowedPriority(
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        any(),
                        any(Pageable.class));
    }

    @Test
    void getOngoingPublicTrips_withRequestingUserId_whenUserFollowsNoOne_shouldReturnAllTrips() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID requestingUserId = UUID.randomUUID();

        Trip trip1 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 1", TripVisibility.PUBLIC);
        trip1.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        Trip trip2 =
                TestEntityFactory.createTrip(UUID.randomUUID(), "Trip 2", TripVisibility.PUBLIC);
        trip2.getTripSettings().setTripStatus(TripStatus.IN_PROGRESS);

        List<Trip> trips = List.of(trip1, trip2);
        when(tripRepository.findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable))
                .thenReturn(new PageImpl<>(trips, pageable, trips.size()));

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("Trip 1");
        assertThat(result.getContent().get(1).name()).isEqualTo("Trip 2");

        verify(tripRepository)
                .findByVisibilityAndStatusInWithPromotedFirst(
                        TripVisibility.PUBLIC, TripStatus.getActiveStatuses(), pageable);
        verify(userFollowRepository).findByFollowerId(requestingUserId);
    }

    @Test
    void
            getOngoingPublicTrips_withRequestingUserId_whenAllTripsAreFromFollowedUsers_shouldReturnAllInOrder() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
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

        List<Trip> trips = List.of(followedTrip1, followedTrip2);
        when(userFollowRepository.findByFollowerId(requestingUserId))
                .thenReturn(List.of(follow1, follow2));
        when(tripRepository.findPublicActiveTripsWithFollowedPriority(
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(trips, PageRequest.of(0, 20), trips.size()));

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("Followed Trip 1");
        assertThat(result.getContent().get(1).name()).isEqualTo("Followed Trip 2");

        verify(userFollowRepository).findByFollowerId(requestingUserId);
    }

    @Test
    void
            getOngoingPublicTrips_withRequestingUserId_whenNoTripsFromFollowedUsers_shouldReturnAllTrips() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
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

        List<Trip> trips = List.of(notFollowedTrip1, notFollowedTrip2);
        when(userFollowRepository.findByFollowerId(requestingUserId)).thenReturn(List.of(follow));
        when(tripRepository.findPublicActiveTripsWithFollowedPriority(
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(trips, PageRequest.of(0, 20), trips.size()));

        // When
        Page<TripDTO> result = tripService.getOngoingPublicTrips(requestingUserId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("Not Followed Trip 1");
        assertThat(result.getContent().get(1).name()).isEqualTo("Not Followed Trip 2");

        verify(userFollowRepository).findByFollowerId(requestingUserId);
    }

    @Test
    void getAllAvailableTripsForUser_whenUserHasOwnTripsAndPublicTrips_shouldReturnAll() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
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

        List<Trip> trips = List.of(ownTrip, publicTrip, friendTrip);
        when(friendshipRepository.findByUserId(userId))
                .thenReturn(
                        List.of(
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId)
                                        .build()));
        when(tripRepository.findAllAvailableTripsForUser(userId, List.of(friendId), pageable))
                .thenReturn(new PageImpl<>(trips, pageable, trips.size()));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).name()).isEqualTo("My Trip");
        assertThat(result.getContent().get(0).tripSettings().visibility())
                .isEqualTo(TripVisibility.PRIVATE);
        assertThat(result.getContent().get(1).name()).isEqualTo("Public Trip");
        assertThat(result.getContent().get(2).name()).isEqualTo("Friend Trip");

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, List.of(friendId), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_whenUserHasNoFriends_shouldReturnOwnAndPublicTrips() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID userId = UUID.randomUUID();
        UUID publicUserId = UUID.randomUUID();

        Trip ownTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), userId, "My Trip", TripVisibility.PRIVATE);
        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), publicUserId, "Public Trip", TripVisibility.PUBLIC);

        List<Trip> trips = List.of(ownTrip, publicTrip);
        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable))
                .thenReturn(new PageImpl<>(trips, pageable, trips.size()));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("My Trip");
        assertThat(result.getContent().get(1).name()).isEqualTo("Public Trip");

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository)
                .findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_whenNoTripsAvailable_shouldReturnEmptyPage() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID userId = UUID.randomUUID();

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository)
                .findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_shouldIncludeOwnPrivateTrips() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID userId = UUID.randomUUID();

        Trip privateTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), userId, "My Private Trip", TripVisibility.PRIVATE);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable))
                .thenReturn(new PageImpl<>(List.of(privateTrip), pageable, 1));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("My Private Trip");
        assertThat(result.getContent().get(0).tripSettings().visibility())
                .isEqualTo(TripVisibility.PRIVATE);

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository)
                .findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_shouldIncludePublicTripsFromOtherUsers() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(),
                        otherUserId,
                        "Other User Public Trip",
                        TripVisibility.PUBLIC);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable))
                .thenReturn(new PageImpl<>(List.of(publicTrip), pageable, 1));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Other User Public Trip");
        assertThat(result.getContent().get(0).tripSettings().visibility())
                .isEqualTo(TripVisibility.PUBLIC);

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository)
                .findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_shouldIncludeProtectedTripsFromFriends() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
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
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId)
                                        .build()));
        when(tripRepository.findAllAvailableTripsForUser(userId, List.of(friendId), pageable))
                .thenReturn(new PageImpl<>(List.of(friendProtectedTrip), pageable, 1));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Friend Protected Trip");

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository).findAllAvailableTripsForUser(userId, List.of(friendId), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_shouldNotIncludeProtectedTripsFromNonFriends() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID userId = UUID.randomUUID();
        UUID publicUserId = UUID.randomUUID();

        Trip publicTrip =
                TestEntityFactory.createTripWithUser(
                        UUID.randomUUID(), publicUserId, "Public Trip", TripVisibility.PUBLIC);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable))
                .thenReturn(new PageImpl<>(List.of(publicTrip), pageable, 1));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(
                        result.getContent().stream()
                                .noneMatch(
                                        dto ->
                                                dto.tripSettings().visibility()
                                                        == TripVisibility.PROTECTED))
                .isTrue();

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository)
                .findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_withMultipleFriends_shouldReturnAllAvailableTrips() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
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

        List<Trip> trips = List.of(ownTrip, publicTrip, friend1Trip, friend2Trip);
        when(friendshipRepository.findByUserId(userId))
                .thenReturn(
                        List.of(
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId1)
                                        .build(),
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(userId)
                                        .friendId(friendId2)
                                        .build()));
        when(tripRepository.findAllAvailableTripsForUser(
                        userId, List.of(friendId1, friendId2), pageable))
                .thenReturn(new PageImpl<>(trips, pageable, trips.size()));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(4);
        assertThat(result.getContent().get(0).name()).isEqualTo("My Trip");
        assertThat(result.getContent().get(1).name()).isEqualTo("Public Trip");
        assertThat(result.getContent().get(2).name()).isEqualTo("Friend 1 Trip");
        assertThat(result.getContent().get(3).name()).isEqualTo("Friend 2 Trip");

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository)
                .findAllAvailableTripsForUser(userId, List.of(friendId1, friendId2), pageable);
    }

    @Test
    void getAllAvailableTripsForUser_shouldMapAllFieldsCorrectly() {
        // Given
        Pageable pageable =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "creationTimestamp"));
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        Trip trip =
                TestEntityFactory.createTripWithUser(
                        tripId, userId, "Test Trip", TripVisibility.PUBLIC);

        when(friendshipRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(tripRepository.findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable))
                .thenReturn(new PageImpl<>(List.of(trip), pageable, 1));

        // When
        Page<TripDTO> result = tripService.getAllAvailableTripsForUser(userId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        TripDTO tripDTO = result.getContent().getFirst();
        assertThat(tripDTO.id()).isEqualTo(tripId.toString());
        assertThat(tripDTO.name()).isEqualTo("Test Trip");
        assertThat(tripDTO.userId()).isEqualTo(userId.toString());
        assertThat(tripDTO.tripSettings().visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(tripDTO.tripSettings().tripStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(tripDTO.enabled()).isTrue();
        assertThat(tripDTO.creationTimestamp()).isNotNull();

        verify(friendshipRepository).findByUserId(userId);
        verify(tripRepository)
                .findAllAvailableTripsForUser(userId, Collections.emptyList(), pageable);
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
        when(tripUpdateRepository.findByTripIdIn(any()))
                .thenReturn(List.of(update1a, update1b, update2a));

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
        when(tripUpdateRepository.findByTripIdIn(any()))
                .thenReturn(List.of(update1, update2));

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
        when(tripUpdateRepository.findByTripIdIn(any()))
                .thenReturn(List.of(update1, update2));

        // When
        TripMaintenanceStatsDTO stats = tripService.getTripMaintenanceStats();

        // Then
        assertThat(stats.totalUpdates()).isEqualTo(2);
        assertThat(stats.updatesWithGeocoding()).isZero();
        assertThat(stats.updatesMissingGeocoding()).isEqualTo(2);
    }
}
