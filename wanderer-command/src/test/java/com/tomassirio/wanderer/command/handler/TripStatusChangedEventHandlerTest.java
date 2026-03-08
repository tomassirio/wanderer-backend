package com.tomassirio.wanderer.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.command.repository.TripDayRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.helper.TripEmbeddedObjectsInitializer;
import com.tomassirio.wanderer.command.service.helper.TripStatusTransitionHandler;
import com.tomassirio.wanderer.commons.domain.ActiveTrip;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDay;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripStatusChangedEventHandlerTest {

    @Mock private TripRepository tripRepository;

    @Mock private ActiveTripRepository activeTripRepository;

    @Mock private TripUpdateRepository tripUpdateRepository;

    @Mock private TripDayRepository tripDayRepository;

    @Mock private TripEmbeddedObjectsInitializer embeddedObjectsInitializer;

    @Mock private TripStatusTransitionHandler statusTransitionHandler;

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
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        verify(embeddedObjectsInitializer)
                .ensureTripSettingsAndGetPreviousStatus(trip, TripStatus.IN_PROGRESS);
        verify(embeddedObjectsInitializer).ensureTripDetails(trip);
        verify(statusTransitionHandler)
                .handleStatusTransition(trip, TripStatus.CREATED, TripStatus.IN_PROGRESS);
        verify(activeTripRepository).save(any(ActiveTrip.class));

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
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

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
        // Handler should not call any methods on embeddedObjectsInitializer when trip is not found
        verifyNoInteractions(embeddedObjectsInitializer);
        verifyNoInteractions(activeTripRepository);
    }

    @Test
    void handle_whenStatusChangedToInProgress_shouldCreateActiveTrip() {
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
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        verify(activeTripRepository).save(any(ActiveTrip.class));
    }

    @Test
    void handle_whenStatusChangedFromInProgressToPaused_shouldRemoveActiveTrip() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("IN_PROGRESS")
                        .newStatus("PAUSED")
                        .build();

        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        Trip trip = Trip.builder().id(tripId).userId(userId).tripSettings(tripSettings).build();
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));

        // When
        handler.handle(event);

        // Then
        verify(activeTripRepository).delete(activeTrip);
    }

    @Test
    void handle_whenStatusChangedFromInProgressToFinished_shouldRemoveActiveTrip() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("IN_PROGRESS")
                        .newStatus("FINISHED")
                        .build();

        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        TripDetails tripDetails = TripDetails.builder().currentDay(1).build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(userId)
                        .tripSettings(tripSettings)
                        .tripDetails(tripDetails)
                        .build();
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));
        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(tripId))
                .thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        verify(activeTripRepository).delete(activeTrip);
    }

    @Test
    void handle_whenStatusChangedFromInProgressToResting_shouldRemoveActiveTrip() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("IN_PROGRESS")
                        .newStatus("RESTING")
                        .build();

        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        Trip trip = Trip.builder().id(tripId).userId(userId).tripSettings(tripSettings).build();
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));
        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(tripId))
                .thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        verify(activeTripRepository).delete(activeTrip);
        assertThat(trip.getTripSettings().getTripStatus()).isEqualTo(TripStatus.RESTING);
    }

    @Test
    void handle_whenStatusChangedFromRestingToInProgress_shouldCreateActiveTrip() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("RESTING")
                        .newStatus("IN_PROGRESS")
                        .build();

        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.RESTING)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        TripDetails tripDetails = TripDetails.builder().currentDay(1).build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(userId)
                        .tripSettings(tripSettings)
                        .tripDetails(tripDetails)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        verify(activeTripRepository).save(any(ActiveTrip.class));
        assertThat(trip.getTripSettings().getTripStatus()).isEqualTo(TripStatus.IN_PROGRESS);
    }

    // --- Lifecycle TripUpdate creation tests ---

    @Test
    void handle_whenCreatedToInProgress_shouldCreateTripStartedUpdate() {
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
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> updateCaptor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(updateCaptor.capture());

        TripUpdate savedUpdate = updateCaptor.getValue();
        assertThat(savedUpdate.getUpdateType()).isEqualTo(UpdateType.TRIP_STARTED);
        assertThat(savedUpdate.getTrip()).isEqualTo(trip);
        assertThat(savedUpdate.getLocation()).isNull();
        assertThat(savedUpdate.getTimestamp()).isNotNull();
    }

    @Test
    void handle_whenInProgressToFinished_shouldCreateTripEndedUpdate() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("IN_PROGRESS")
                        .newStatus("FINISHED")
                        .build();

        Trip trip = createTripWithDetails(tripId, userId, TripStatus.IN_PROGRESS);
        trip.getTripDetails().setCurrentDay(1);
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));
        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(tripId))
                .thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> updateCaptor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(updateCaptor.capture());

        TripUpdate savedUpdate = updateCaptor.getValue();
        assertThat(savedUpdate.getUpdateType()).isEqualTo(UpdateType.TRIP_ENDED);
    }

    @Test
    void handle_whenInProgressToResting_shouldCreateDayEndUpdate() {
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
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));
        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(tripId))
                .thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> updateCaptor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(updateCaptor.capture());

        TripUpdate savedUpdate = updateCaptor.getValue();
        assertThat(savedUpdate.getUpdateType()).isEqualTo(UpdateType.DAY_END);
    }

    @Test
    void handle_whenRestingToInProgress_shouldCreateDayStartUpdate() {
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
        trip.getTripDetails().setCurrentDay(1);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> updateCaptor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(updateCaptor.capture());

        TripUpdate savedUpdate = updateCaptor.getValue();
        assertThat(savedUpdate.getUpdateType()).isEqualTo(UpdateType.DAY_START);
    }

    @Test
    void handle_whenInProgressToPaused_shouldNotCreateLifecycleUpdate() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("IN_PROGRESS")
                        .newStatus("PAUSED")
                        .build();

        Trip trip = createTripWithDetails(tripId, userId, TripStatus.IN_PROGRESS);
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));

        // When
        handler.handle(event);

        // Then
        verify(tripUpdateRepository, never()).save(any(TripUpdate.class));
    }

    // --- TripDay management tests ---

    @Test
    void handle_whenCreatedToInProgress_shouldCreateDay1() {
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
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getDayNumber()).isEqualTo(1);
        assertThat(savedDay.getStartTimestamp()).isNotNull();
        assertThat(savedDay.getEndTimestamp()).isNull();
        assertThat(trip.getTripDetails().getCurrentDay()).isEqualTo(1);
    }

    @Test
    void handle_whenInProgressToResting_shouldCloseCurrentDay() {
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
        trip.getTripDetails().setCurrentDay(1);
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        TripDay openDay =
                TripDay.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .dayNumber(1)
                        .startTimestamp(Instant.now().minusSeconds(3600))
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));
        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(tripId))
                .thenReturn(Optional.of(openDay));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getEndTimestamp()).isNotNull();
        assertThat(savedDay.getDayNumber()).isEqualTo(1);
    }

    @Test
    void handle_whenRestingToInProgress_shouldCreateNextDay() {
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
        trip.getTripDetails().setCurrentDay(1);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getDayNumber()).isEqualTo(2);
        assertThat(savedDay.getStartTimestamp()).isNotNull();
        assertThat(savedDay.getEndTimestamp()).isNull();
        assertThat(trip.getTripDetails().getCurrentDay()).isEqualTo(2);
    }

    @Test
    void handle_whenFinished_shouldCloseOpenDay() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .previousStatus("IN_PROGRESS")
                        .newStatus("FINISHED")
                        .build();

        Trip trip = createTripWithDetails(tripId, userId, TripStatus.IN_PROGRESS);
        trip.getTripDetails().setCurrentDay(3);
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();

        TripDay openDay =
                TripDay.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .dayNumber(3)
                        .startTimestamp(Instant.now().minusSeconds(3600))
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));
        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(tripId))
                .thenReturn(Optional.of(openDay));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getEndTimestamp()).isNotNull();
        assertThat(savedDay.getDayNumber()).isEqualTo(3);
    }

    // --- resolveLifecycleUpdateType tests ---

    @Test
    void resolveLifecycleUpdateType_whenCreatedToInProgress_shouldReturnTripStarted() {
        assertThat(handler.resolveLifecycleUpdateType(TripStatus.CREATED, TripStatus.IN_PROGRESS))
                .isEqualTo(UpdateType.TRIP_STARTED);
    }

    @Test
    void resolveLifecycleUpdateType_whenAnyToFinished_shouldReturnTripEnded() {
        assertThat(handler.resolveLifecycleUpdateType(TripStatus.IN_PROGRESS, TripStatus.FINISHED))
                .isEqualTo(UpdateType.TRIP_ENDED);
    }

    @Test
    void resolveLifecycleUpdateType_whenInProgressToResting_shouldReturnDayEnd() {
        assertThat(handler.resolveLifecycleUpdateType(TripStatus.IN_PROGRESS, TripStatus.RESTING))
                .isEqualTo(UpdateType.DAY_END);
    }

    @Test
    void resolveLifecycleUpdateType_whenRestingToInProgress_shouldReturnDayStart() {
        assertThat(handler.resolveLifecycleUpdateType(TripStatus.RESTING, TripStatus.IN_PROGRESS))
                .isEqualTo(UpdateType.DAY_START);
    }

    @Test
    void resolveLifecycleUpdateType_whenInProgressToPaused_shouldReturnNull() {
        assertThat(handler.resolveLifecycleUpdateType(TripStatus.IN_PROGRESS, TripStatus.PAUSED))
                .isNull();
    }

    @Test
    void resolveLifecycleUpdateType_whenPausedToInProgress_shouldReturnNull() {
        assertThat(handler.resolveLifecycleUpdateType(TripStatus.PAUSED, TripStatus.IN_PROGRESS))
                .isNull();
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
