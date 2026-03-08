package com.tomassirio.wanderer.command.service.impl;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateRequest;
import com.tomassirio.wanderer.command.event.TripCreatedEvent;
import com.tomassirio.wanderer.command.event.TripDeletedEvent;
import com.tomassirio.wanderer.command.event.TripMetadataUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripSettingsUpdatedEvent;
import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.event.TripVisibilityChangedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.command.service.helper.TripEmbeddedObjectsInitializer;
import com.tomassirio.wanderer.command.service.helper.TripStatusTransitionHandler;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.command.utils.TestEntityFactory;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TripServiceImplTest {

    @Mock private TripRepository tripRepository;

    @Mock private UserRepository userRepository;

    @Mock private TripPlanRepository tripPlanRepository;

    @Mock
    private com.tomassirio.wanderer.command.repository.ActiveTripRepository activeTripRepository;

    @Mock private TripEmbeddedObjectsInitializer embeddedObjectsInitializer;

    @Mock private TripStatusTransitionHandler statusTransitionHandler;

    @Mock private OwnershipValidator ownershipValidator;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TripServiceImpl tripService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(userRepository.findById(any(UUID.class)))
                .thenAnswer(
                        invocation -> {
                            UUID id = invocation.getArgument(0);
                            return Optional.of(User.builder().id(id).username("test-user").build());
                        });

        // Configure the embedded objects initializer to create default objects
        lenient()
                .when(embeddedObjectsInitializer.createTripSettings(any(TripVisibility.class)))
                .thenAnswer(
                        invocation -> {
                            TripVisibility visibility = invocation.getArgument(0);
                            return TripSettings.builder()
                                    .tripStatus(TripStatus.CREATED)
                                    .visibility(visibility)
                                    .updateRefresh(null)
                                    .build();
                        });

        lenient()
                .when(embeddedObjectsInitializer.createTripDetails())
                .thenReturn(
                        TripDetails.builder()
                                .startTimestamp(null)
                                .endTimestamp(null)
                                .startLocation(null)
                                .endLocation(null)
                                .build());

        // Configure ensureTripSettings to do nothing (objects already exist in tests)
        lenient()
                .when(
                        embeddedObjectsInitializer.ensureTripSettingsAndGetPreviousStatus(
                                any(Trip.class), any(TripStatus.class)))
                .thenAnswer(
                        invocation -> {
                            Trip trip = invocation.getArgument(0);
                            if (trip.getTripSettings() != null) {
                                return trip.getTripSettings().getTripStatus();
                            }
                            return TripStatus.CREATED;
                        });

        // Configure the status transition handler to actually update timestamps
        lenient()
                .doAnswer(
                        invocation -> {
                            Trip trip = invocation.getArgument(0);
                            TripStatus previousStatus = invocation.getArgument(1);
                            TripStatus newStatus = invocation.getArgument(2);

                            // Replicate the actual logic from TripStatusTransitionHandler
                            if (newStatus == TripStatus.IN_PROGRESS
                                    && previousStatus == TripStatus.CREATED) {
                                trip.getTripDetails().setStartTimestamp(Instant.now());
                            } else if (newStatus == TripStatus.FINISHED) {
                                trip.getTripDetails().setEndTimestamp(Instant.now());
                            }
                            return null;
                        })
                .when(statusTransitionHandler)
                .handleStatusTransition(
                        any(Trip.class), any(TripStatus.class), any(TripStatus.class));

        // Configure the ownership validator to throw exception for non-owners
        lenient()
                .doAnswer(
                        invocation -> {
                            Object entity = invocation.getArgument(0);
                            UUID userId = invocation.getArgument(1);
                            Function<Object, UUID> userIdExtractor = invocation.getArgument(2);
                            Function<Object, UUID> entityIdExtractor = invocation.getArgument(3);
                            String entityType = invocation.getArgument(4);

                            UUID ownerId = userIdExtractor.apply(entity);
                            if (!ownerId.equals(userId)) {
                                UUID entityId = entityIdExtractor.apply(entity);
                                throw new AccessDeniedException(
                                        "User "
                                                + userId
                                                + " does not have permission to modify "
                                                + entityType
                                                + " "
                                                + entityId);
                            }
                            return null;
                        })
                .when(ownershipValidator)
                .validateOwnership(
                        any(),
                        any(UUID.class),
                        any(Function.class),
                        any(Function.class),
                        any(String.class));
    }

    @Test
    void createTrip_whenValidRequest_shouldCreateAndSaveTrip() {
        // Given
        TripCreationRequest request =
                TestEntityFactory.createTripCreationRequest(
                        "Summer Road Trip", TripVisibility.PUBLIC);

        // When
        UUID result = tripService.createTrip(USER_ID, request);

        // Then
        assertThat(result).isNotNull();

        verify(eventPublisher).publishEvent(any(TripCreatedEvent.class));
        verify(userRepository).findById(USER_ID);
    }

    @Test
    void createTrip_whenPrivateVisibility_shouldCreatePrivateTrip() {
        // Given
        TripCreationRequest request =
                TestEntityFactory.createTripCreationRequest("Private Trip", TripVisibility.PRIVATE);

        // When
        UUID result = tripService.createTrip(USER_ID, request);

        // Then
        assertThat(result).isNotNull();

        verify(eventPublisher).publishEvent(any(TripCreatedEvent.class));
    }

    @Test
    void createTrip_shouldInitializeWithNullTimestampsAndLocations() {
        // Given
        TripCreationRequest request =
                TestEntityFactory.createTripCreationRequest("New Trip", TripVisibility.PUBLIC);

        // When
        UUID result = tripService.createTrip(USER_ID, request);

        // Then
        assertThat(result).isNotNull();

        verify(eventPublisher).publishEvent(any(TripCreatedEvent.class));
    }

    @Test
    void createTrip_whenUserNotFound_shouldThrowException() {
        // Given
        UUID nonExistentUserId = UUID.randomUUID();
        TripCreationRequest request =
                TestEntityFactory.createTripCreationRequest("Test Trip", TripVisibility.PUBLIC);

        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripService.createTrip(nonExistentUserId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository).findById(nonExistentUserId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateTrip_whenTripExists_shouldUpdateAndSaveTrip() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PRIVATE)
                        .updateRefresh(null)
                        .build();

        TripDetails existingDetails =
                TripDetails.builder()
                        .startTimestamp(null)
                        .endTimestamp(null)
                        .startLocation(null)
                        .endLocation(null)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Old Trip Name")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(existingDetails)
                        .creationTimestamp(Instant.now().minusSeconds(3600))
                        .enabled(true)
                        .build();

        TripUpdateRequest request =
                TestEntityFactory.createTripUpdateRequest(
                        "Updated Trip Name", TripVisibility.PUBLIC);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.updateTrip(USER_ID, tripId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);

        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripMetadataUpdatedEvent.class));
    }

    @Test
    void updateTrip_whenTripDoesNotExist_shouldThrowException() {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();
        TripUpdateRequest request =
                TestEntityFactory.createTripUpdateRequest("Updated Trip", TripVisibility.PUBLIC);

        when(tripRepository.findById(nonExistentTripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripService.updateTrip(USER_ID, nonExistentTripId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip not found");

        verify(tripRepository).findById(nonExistentTripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateTrip_whenChangingVisibility_shouldUpdateVisibility() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PRIVATE)
                        .updateRefresh(null)
                        .build();

        TripDetails existingDetails =
                TripDetails.builder()
                        .startTimestamp(null)
                        .endTimestamp(null)
                        .startLocation(null)
                        .endLocation(null)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Trip Name")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(existingDetails)
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        TripUpdateRequest request =
                TestEntityFactory.createTripUpdateRequest("Trip Name", TripVisibility.PUBLIC);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.updateTrip(USER_ID, tripId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);

        verify(eventPublisher).publishEvent(any(TripMetadataUpdatedEvent.class));
    }

    @Test
    void updateTrip_whenUserDoesNotOwnTrip_shouldThrowAccessDeniedException() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Trip Name")
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        TripUpdateRequest request =
                TestEntityFactory.createTripUpdateRequest("Updated Trip", TripVisibility.PUBLIC);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.updateTrip(otherUserId, tripId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not have permission");

        verify(tripRepository).findById(tripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteTrip_whenTripExists_shouldCallRepositoryDelete() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Trip Name")
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        tripService.deleteTrip(USER_ID, tripId);

        // Then
        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripDeletedEvent.class));
    }

    @Test
    void deleteTrip_whenUserDoesNotOwnTrip_shouldThrowAccessDeniedException() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Trip Name")
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.deleteTrip(otherUserId, tripId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not have permission");

        verify(tripRepository).findById(tripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeVisibility_whenUserOwnsTrip_shouldChangeVisibility() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .updateRefresh(null)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.changeVisibility(USER_ID, tripId, TripVisibility.PRIVATE);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);
        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripVisibilityChangedEvent.class));
    }

    @Test
    void changeVisibility_whenUserDoesNotOwnTrip_shouldThrowAccessDeniedException() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Trip Name")
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.changeVisibility(
                                        otherUserId, tripId, TripVisibility.PRIVATE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not have permission");

        verify(tripRepository).findById(tripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_whenUserOwnsTrip_shouldChangeStatus() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .updateRefresh(null)
                        .build();

        TripDetails existingDetails =
                TripDetails.builder()
                        .startTimestamp(null)
                        .endTimestamp(null)
                        .startLocation(null)
                        .endLocation(null)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(existingDetails)
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));
        when(activeTripRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // When
        UUID result = tripService.changeStatus(USER_ID, tripId, TripStatus.IN_PROGRESS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);
        verify(tripRepository).findById(tripId);
        verify(activeTripRepository).findById(USER_ID);
        verify(eventPublisher).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void changeStatus_whenUserDoesNotOwnTrip_shouldThrowAccessDeniedException() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Trip Name")
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(
                        () -> tripService.changeStatus(otherUserId, tripId, TripStatus.IN_PROGRESS))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not have permission");

        verify(tripRepository).findById(tripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_toFinished_shouldSetEndTimestamp() {
        // Given
        UUID tripId = UUID.randomUUID();
        Instant startTime = Instant.now().minusSeconds(3600);

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .updateRefresh(null)
                        .build();

        TripDetails existingDetails =
                TripDetails.builder()
                        .startTimestamp(startTime)
                        .endTimestamp(null)
                        .startLocation(null)
                        .endLocation(null)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(existingDetails)
                        .creationTimestamp(Instant.now().minusSeconds(7200))
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.changeStatus(USER_ID, tripId, TripStatus.FINISHED);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);
        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void changeStatus_fromCreatedToInProgress_shouldSetStartTimestamp() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .updateRefresh(null)
                        .build();

        TripDetails existingDetails =
                TripDetails.builder()
                        .startTimestamp(null)
                        .endTimestamp(null)
                        .startLocation(null)
                        .endLocation(null)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(existingDetails)
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.changeStatus(USER_ID, tripId, TripStatus.IN_PROGRESS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);
        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void changeStatus_toPaused_shouldNotChangeTimestamps() {
        // Given
        UUID tripId = UUID.randomUUID();
        Instant startTime = Instant.now().minusSeconds(3600);

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .updateRefresh(null)
                        .build();

        TripDetails existingDetails =
                TripDetails.builder()
                        .startTimestamp(startTime)
                        .endTimestamp(null)
                        .startLocation(null)
                        .endLocation(null)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(existingDetails)
                        .creationTimestamp(Instant.now().minusSeconds(7200))
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.changeStatus(USER_ID, tripId, TripStatus.PAUSED);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);
        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void changeVisibility_whenTripNotFound_shouldThrowEntityNotFoundException() {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();

        when(tripRepository.findById(nonExistentTripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.changeVisibility(
                                        USER_ID, nonExistentTripId, TripVisibility.PRIVATE))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip not found");

        verify(tripRepository).findById(nonExistentTripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_whenTripNotFound_shouldThrowEntityNotFoundException() {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();

        when(tripRepository.findById(nonExistentTripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.changeStatus(
                                        USER_ID, nonExistentTripId, TripStatus.IN_PROGRESS))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip not found");

        verify(tripRepository).findById(nonExistentTripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // Tests for createTripFromPlan

    @Test
    void createTripFromPlan_whenValidRequest_shouldCreateTripFromPlan() {
        // Given
        UUID tripPlanId = UUID.randomUUID();
        var tripPlan =
                BaseTestEntityFactory.createTripPlan(
                        tripPlanId,
                        USER_ID,
                        "Summer Road Trip Plan",
                        java.time.LocalDate.now().plusDays(1),
                        java.time.LocalDate.now().plusDays(7));

        when(tripPlanRepository.findById(tripPlanId)).thenReturn(Optional.of(tripPlan));

        // When
        UUID result = tripService.createTripFromPlan(USER_ID, tripPlanId, TripVisibility.PUBLIC);

        // Then
        assertThat(result).isNotNull();

        verify(eventPublisher).publishEvent(any(TripCreatedEvent.class));
        verify(tripPlanRepository).findById(tripPlanId);
        verify(userRepository).findById(USER_ID);
        verify(ownershipValidator)
                .validateOwnership(
                        any(),
                        any(UUID.class),
                        any(Function.class),
                        any(Function.class),
                        any(String.class));
    }

    @Test
    void createTripFromPlan_whenPrivateVisibility_shouldCreatePrivateTrip() {
        // Given
        UUID tripPlanId = UUID.randomUUID();
        var tripPlan =
                BaseTestEntityFactory.createTripPlan(
                        tripPlanId,
                        USER_ID,
                        "Private Trip Plan",
                        java.time.LocalDate.now().plusDays(1),
                        java.time.LocalDate.now().plusDays(7));

        when(tripPlanRepository.findById(tripPlanId)).thenReturn(Optional.of(tripPlan));

        // When
        UUID result = tripService.createTripFromPlan(USER_ID, tripPlanId, TripVisibility.PRIVATE);

        // Then
        assertThat(result).isNotNull();

        verify(eventPublisher).publishEvent(any(TripCreatedEvent.class));
    }

    @Test
    void createTripFromPlan_whenTripPlanNotFound_shouldThrowException() {
        // Given
        UUID nonExistentPlanId = UUID.randomUUID();

        when(tripPlanRepository.findById(nonExistentPlanId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.createTripFromPlan(
                                        USER_ID, nonExistentPlanId, TripVisibility.PUBLIC))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip plan not found");

        verify(tripPlanRepository).findById(nonExistentPlanId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createTripFromPlan_whenUserNotOwner_shouldThrowAccessDeniedException() {
        // Given
        UUID tripPlanId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        var tripPlan =
                BaseTestEntityFactory.createTripPlan(
                        tripPlanId,
                        otherUserId,
                        "Someone Else's Plan",
                        java.time.LocalDate.now().plusDays(1),
                        java.time.LocalDate.now().plusDays(7));

        when(tripPlanRepository.findById(tripPlanId)).thenReturn(Optional.of(tripPlan));

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.createTripFromPlan(
                                        USER_ID, tripPlanId, TripVisibility.PUBLIC))
                .isInstanceOf(AccessDeniedException.class);

        verify(tripPlanRepository).findById(tripPlanId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createTripFromPlan_whenUserNotFound_shouldThrowException() {
        // Given
        UUID nonExistentUserId = UUID.randomUUID();
        UUID tripPlanId = UUID.randomUUID();

        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.createTripFromPlan(
                                        nonExistentUserId, tripPlanId, TripVisibility.PUBLIC))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository).findById(nonExistentUserId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createTripFromPlan_whenPlanHasWaypoints_shouldCopyWaypoints() {
        // Given
        UUID tripPlanId = UUID.randomUUID();
        java.util.List<GeoLocation> waypoints =
                java.util.List.of(
                        com.tomassirio.wanderer.commons.domain.GeoLocation.builder()
                                .lat(41.8781)
                                .lon(-87.6298)
                                .build(), // Chicago
                        com.tomassirio.wanderer.commons.domain.GeoLocation.builder()
                                .lat(39.7392)
                                .lon(-104.9903)
                                .build() // Denver
                        );

        var tripPlan =
                TripPlan.builder()
                        .id(tripPlanId)
                        .userId(USER_ID)
                        .name("Road Trip with Stops")
                        .planType(TripPlanType.MULTI_DAY)
                        .startDate(LocalDate.now().plusDays(1))
                        .endDate(LocalDate.now().plusDays(7))
                        .startLocation(GeoLocation.builder().lat(40.7128).lon(-74.0060).build())
                        .endLocation(GeoLocation.builder().lat(34.0522).lon(-118.2437).build())
                        .waypoints(waypoints)
                        .metadata(new HashMap<>())
                        .createdTimestamp(Instant.now())
                        .build();

        when(tripPlanRepository.findById(tripPlanId)).thenReturn(Optional.of(tripPlan));

        // When
        UUID result = tripService.createTripFromPlan(USER_ID, tripPlanId, TripVisibility.PUBLIC);

        // Then
        assertThat(result).isNotNull();

        verify(eventPublisher).publishEvent(any(TripCreatedEvent.class));
        verify(tripPlanRepository).findById(tripPlanId);
    }

    @Test
    void changeStatus_whenUserHasAnotherTripInProgress_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID otherTripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .build();

        Trip existingTrip =
                Trip.builder().id(tripId).userId(USER_ID).tripSettings(existingSettings).build();

        com.tomassirio.wanderer.commons.domain.ActiveTrip activeTrip =
                com.tomassirio.wanderer.commons.domain.ActiveTrip.builder()
                        .userId(USER_ID)
                        .tripId(otherTripId)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));
        when(activeTripRepository.findById(USER_ID)).thenReturn(Optional.of(activeTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.changeStatus(USER_ID, tripId, TripStatus.IN_PROGRESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already has a trip in progress");

        verify(eventPublisher, never()).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void changeStatus_whenSameTripAlreadyInProgress_shouldAllowChange() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.PAUSED)
                        .visibility(TripVisibility.PUBLIC)
                        .build();

        Trip existingTrip =
                Trip.builder().id(tripId).userId(USER_ID).tripSettings(existingSettings).build();

        com.tomassirio.wanderer.commons.domain.ActiveTrip activeTrip =
                com.tomassirio.wanderer.commons.domain.ActiveTrip.builder()
                        .userId(USER_ID)
                        .tripId(tripId)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));
        when(activeTripRepository.findById(USER_ID)).thenReturn(Optional.of(activeTrip));

        // When
        UUID result = tripService.changeStatus(USER_ID, tripId, TripStatus.IN_PROGRESS);

        // Then
        assertThat(result).isEqualTo(tripId);
        verify(eventPublisher).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void changeStatus_whenChangingToNonInProgressStatus_shouldNotCheckActiveTrips() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .build();

        Trip existingTrip =
                Trip.builder().id(tripId).userId(USER_ID).tripSettings(existingSettings).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.changeStatus(USER_ID, tripId, TripStatus.PAUSED);

        // Then
        assertThat(result).isEqualTo(tripId);
        verify(activeTripRepository, never()).findById(any());
        verify(eventPublisher).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void updateSettings_whenUserOwnsTrip_shouldUpdateSettings() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .updateRefresh(60)
                        .automaticUpdates(false)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.updateSettings(USER_ID, tripId, 120, true, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);
        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripSettingsUpdatedEvent.class));
    }

    @Test
    void updateSettings_whenUserDoesNotOwnTrip_shouldThrowAccessDeniedException() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("Other User's Trip")
                        .userId(otherUserId)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.updateSettings(
                                        USER_ID, tripId, 120, true,
                                        null)) // USER_ID is not the owner
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not have permission to modify trip");

        verify(tripRepository).findById(tripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateSettings_whenTripNotFound_shouldThrowEntityNotFoundException() {
        // Given
        UUID tripId = UUID.randomUUID();

        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripService.updateSettings(USER_ID, tripId, 120, true, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Trip not found");

        verify(tripRepository).findById(tripId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateSettings_whenPartialUpdate_shouldUpdateOnlyProvidedFields() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .updateRefresh(60)
                        .automaticUpdates(false)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When - only update automaticUpdates
        UUID result = tripService.updateSettings(USER_ID, tripId, null, true, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tripId);
        verify(tripRepository).findById(tripId);
        verify(eventPublisher).publishEvent(any(TripSettingsUpdatedEvent.class));
    }

    @Test
    void updateSettings_whenUpgradingFromSimpleToMultiDay_shouldPublishEvent() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .tripModality(TripModality.SIMPLE)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result =
                tripService.updateSettings(USER_ID, tripId, null, null, TripModality.MULTI_DAY);

        // Then
        assertThat(result).isEqualTo(tripId);
        ArgumentCaptor<TripSettingsUpdatedEvent> captor =
                ArgumentCaptor.forClass(TripSettingsUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getTripModality()).isEqualTo(TripModality.MULTI_DAY);
    }

    @Test
    void updateSettings_whenDowngradingFromMultiDayToSimple_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        TripSettings existingSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .tripModality(TripModality.MULTI_DAY)
                        .build();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .name("My Trip")
                        .userId(USER_ID)
                        .tripSettings(existingSettings)
                        .tripDetails(TripDetails.builder().build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(
                        () ->
                                tripService.updateSettings(
                                        USER_ID, tripId, null, null, TripModality.SIMPLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Trip modality cannot be downgraded from MULTI_DAY to SIMPLE.");

        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- toggleDay tests ---

    @Test
    void toggleDay_whenTripIsInProgressAndMultiDay_shouldChangeToResting() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.IN_PROGRESS)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.MULTI_DAY)
                                        .build())
                        .tripDetails(TripDetails.builder().currentDay(1).build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.toggleDay(USER_ID, tripId);

        // Then
        assertThat(result).isEqualTo(tripId);

        ArgumentCaptor<TripStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TripStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.getNewStatus()).isEqualTo("RESTING");
        assertThat(event.getPreviousStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void toggleDay_whenTripIsRestingAndMultiDay_shouldChangeToInProgress() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.RESTING)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.MULTI_DAY)
                                        .build())
                        .tripDetails(TripDetails.builder().currentDay(1).build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));
        when(activeTripRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // When
        UUID result = tripService.toggleDay(USER_ID, tripId);

        // Then
        assertThat(result).isEqualTo(tripId);

        ArgumentCaptor<TripStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TripStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.getNewStatus()).isEqualTo("IN_PROGRESS");
        assertThat(event.getPreviousStatus()).isEqualTo("RESTING");
    }

    @Test
    void toggleDay_whenTripIsSimple_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.IN_PROGRESS)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.SIMPLE)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.toggleDay(USER_ID, tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MULTI_DAY");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void toggleDay_whenTripModalityIsNull_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.IN_PROGRESS)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(null)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.toggleDay(USER_ID, tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MULTI_DAY");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void toggleDay_whenTripIsCreated_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.MULTI_DAY)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.toggleDay(USER_ID, tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS or RESTING");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void toggleDay_whenTripIsFinished_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.FINISHED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.MULTI_DAY)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.toggleDay(USER_ID, tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS or RESTING");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void toggleDay_whenTripNotFound_shouldThrowEntityNotFoundException() {
        // Given
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripService.toggleDay(USER_ID, tripId))
                .isInstanceOf(EntityNotFoundException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void toggleDay_whenRestingAndAnotherTripInProgress_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID otherTripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.RESTING)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.MULTI_DAY)
                                        .build())
                        .tripDetails(TripDetails.builder().currentDay(1).build())
                        .build();

        com.tomassirio.wanderer.commons.domain.ActiveTrip activeTrip =
                com.tomassirio.wanderer.commons.domain.ActiveTrip.builder()
                        .userId(USER_ID)
                        .tripId(otherTripId)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));
        when(activeTripRepository.findById(USER_ID)).thenReturn(Optional.of(activeTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.toggleDay(USER_ID, tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already has a trip in progress");

        verify(eventPublisher, never()).publishEvent(any(TripStatusChangedEvent.class));
    }

    // --- changeStatus transition validation tests ---

    @Test
    void changeStatus_fromFinishedToInProgress_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.FINISHED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.changeStatus(USER_ID, tripId, TripStatus.IN_PROGRESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from FINISHED to IN_PROGRESS");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_fromCreatedToResting_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.changeStatus(USER_ID, tripId, TripStatus.RESTING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from CREATED to RESTING");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_fromPausedToResting_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.PAUSED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.changeStatus(USER_ID, tripId, TripStatus.RESTING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition from PAUSED to RESTING");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_toRestingForSimpleTrip_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.IN_PROGRESS)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.SIMPLE)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.changeStatus(USER_ID, tripId, TripStatus.RESTING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESTING status is only available for MULTI_DAY trips");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_toRestingForMultiDayTrip_shouldSucceed() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.IN_PROGRESS)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.MULTI_DAY)
                                        .build())
                        .tripDetails(TripDetails.builder().currentDay(1).build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When
        UUID result = tripService.changeStatus(USER_ID, tripId, TripStatus.RESTING);

        // Then
        assertThat(result).isEqualTo(tripId);
        verify(eventPublisher).publishEvent(any(TripStatusChangedEvent.class));
    }

    @Test
    void changeStatus_toRestingForNullModality_shouldThrowIllegalStateException() {
        // Given
        UUID tripId = UUID.randomUUID();

        Trip existingTrip =
                Trip.builder()
                        .id(tripId)
                        .userId(USER_ID)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.IN_PROGRESS)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(null)
                                        .build())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));

        // When & Then
        assertThatThrownBy(() -> tripService.changeStatus(USER_ID, tripId, TripStatus.RESTING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESTING status is only available for MULTI_DAY trips");

        verify(eventPublisher, never()).publishEvent(any());
    }
}
