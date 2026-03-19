package com.tomassirio.wanderer.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.AchievementService;
import com.tomassirio.wanderer.command.service.helper.ActiveTripManager;
import com.tomassirio.wanderer.command.service.helper.LifecycleTripUpdateManager;
import com.tomassirio.wanderer.command.service.helper.TripDayManager;
import com.tomassirio.wanderer.command.service.helper.TripEmbeddedObjectsInitializer;
import com.tomassirio.wanderer.command.service.helper.TripStatusTransitionHandler;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripStatusChangedEventHandlerTest {

    @Mock private TripRepository tripRepository;

    @Mock private TripEmbeddedObjectsInitializer embeddedObjectsInitializer;

    @Mock private TripStatusTransitionHandler statusTransitionHandler;

    @Mock private LifecycleTripUpdateManager lifecycleTripUpdateManager;

    @Mock private TripDayManager tripDayManager;

    @Mock private ActiveTripManager activeTripManager;

    @Mock private AchievementService achievementService;

    @InjectMocks private TripStatusChangedEventHandler handler;

    @Test
    void handle_whenTripExists_shouldUpdateTripStatus() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("CREATED")
                        .newStatus("IN_PROGRESS")
                        .build();

        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        TripDetails tripDetails = TripDetails.builder().build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(userId)
                        .tripSettings(tripSettings)
                        .tripDetails(tripDetails)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        verify(embeddedObjectsInitializer)
                .ensureTripSettingsAndGetPreviousStatus(trip, TripStatus.IN_PROGRESS);
        verify(embeddedObjectsInitializer).ensureTripDetails(trip);
        verify(statusTransitionHandler)
                .handleStatusTransition(trip, TripStatus.CREATED, TripStatus.IN_PROGRESS);
        verify(lifecycleTripUpdateManager)
                .createLifecycleTripUpdate(trip, TripStatus.CREATED, TripStatus.IN_PROGRESS);
        verify(tripDayManager).manageTripDays(trip, TripStatus.CREATED, TripStatus.IN_PROGRESS);
        verify(activeTripManager).manageActiveTrip(userId, tripId, TripStatus.IN_PROGRESS);
        verify(achievementService).checkAndUnlockAchievements(tripId);

        // Entity is managed, no need to verify save
        assertThat(trip.getTripSettings().getTripStatus()).isEqualTo(TripStatus.IN_PROGRESS);
    }

    @Test
    void handle_whenPreviousStatusIsNull_shouldHandleStatusTransitionWithNull() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus(null)
                        .newStatus("CREATED")
                        .build();

        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        Trip trip = Trip.builder().id(tripId).userId(userId).tripSettings(tripSettings).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        verify(statusTransitionHandler).handleStatusTransition(trip, null, TripStatus.CREATED);
    }

    @Test
    void handle_whenTripNotFound_shouldNotUpdateOrSave() {
        // Given
        UUID tripId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("CREATED")
                        .newStatus("IN_PROGRESS")
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        verify(tripRepository).findById(tripId);
        verifyNoInteractions(embeddedObjectsInitializer);
        verifyNoInteractions(lifecycleTripUpdateManager);
        verifyNoInteractions(tripDayManager);
        verifyNoInteractions(activeTripManager);
        verifyNoInteractions(achievementService);
    }

    // --- Delegation tests ---

    @Test
    void handle_whenTripExists_shouldDelegateToLifecycleTripUpdateManager() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("IN_PROGRESS")
                        .newStatus("RESTING")
                        .build();

        Trip trip = createTripWithDetails(tripId, userId, TripStatus.IN_PROGRESS);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        verify(lifecycleTripUpdateManager)
                .createLifecycleTripUpdate(trip, TripStatus.IN_PROGRESS, TripStatus.RESTING);
    }

    @Test
    void handle_whenTripExists_shouldDelegateToTripDayManager() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("CREATED")
                        .newStatus("IN_PROGRESS")
                        .build();

        Trip trip = createTripWithDetails(tripId, userId, TripStatus.CREATED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        verify(tripDayManager).manageTripDays(trip, TripStatus.CREATED, TripStatus.IN_PROGRESS);
    }

    @Test
    void handle_whenTripExists_shouldDelegateToActiveTripManager() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("CREATED")
                        .newStatus("IN_PROGRESS")
                        .build();

        Trip trip = createTripWithDetails(tripId, userId, TripStatus.CREATED);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        verify(activeTripManager).manageActiveTrip(userId, tripId, TripStatus.IN_PROGRESS);
    }

    @Test
    void handle_whenTripExists_shouldDelegateToAchievementService() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("RESTING")
                        .newStatus("IN_PROGRESS")
                        .build();

        Trip trip = createTripWithDetails(tripId, userId, TripStatus.RESTING);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        verify(achievementService).checkAndUnlockAchievements(tripId);
    }

    // --- Helper methods ---

    private Trip createTripWithDetails(UUID tripId, UUID userId, TripStatus status) {
        TripSettings tripSettings =
                TripSettings.builder().tripStatus(status).visibility(TripVisibility.PUBLIC).build();
        TripDetails tripDetails = TripDetails.builder().build();
        return Trip.builder()
                .id(tripId)
                .userId(userId)
                .tripSettings(tripSettings)
                .tripDetails(tripDetails)
                .build();
    }
}
