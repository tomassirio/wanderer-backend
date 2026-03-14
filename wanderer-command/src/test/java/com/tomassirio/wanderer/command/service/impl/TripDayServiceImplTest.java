package com.tomassirio.wanderer.command.service.impl;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.ActiveTrip;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TripDayServiceImplTest {

    @Mock private TripRepository tripRepository;

    @Mock private ActiveTripRepository activeTripRepository;

    @Mock private OwnershipValidator ownershipValidator;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TripDayServiceImpl tripDayService;

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
        UUID result = tripDayService.toggleDay(USER_ID, tripId);

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
        UUID result = tripDayService.toggleDay(USER_ID, tripId);

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
        assertThatThrownBy(() -> tripDayService.toggleDay(USER_ID, tripId))
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
        assertThatThrownBy(() -> tripDayService.toggleDay(USER_ID, tripId))
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
        assertThatThrownBy(() -> tripDayService.toggleDay(USER_ID, tripId))
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
        assertThatThrownBy(() -> tripDayService.toggleDay(USER_ID, tripId))
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
        assertThatThrownBy(() -> tripDayService.toggleDay(USER_ID, tripId))
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

        ActiveTrip activeTrip = ActiveTrip.builder().userId(USER_ID).tripId(otherTripId).build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(existingTrip));
        when(activeTripRepository.findById(USER_ID)).thenReturn(Optional.of(activeTrip));

        // When & Then
        assertThatThrownBy(() -> tripDayService.toggleDay(USER_ID, tripId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already has a trip in progress");

        verify(eventPublisher, never()).publishEvent(any(TripStatusChangedEvent.class));
    }
}
