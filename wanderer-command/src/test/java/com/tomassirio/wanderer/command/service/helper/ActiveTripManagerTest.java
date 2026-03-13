package com.tomassirio.wanderer.command.service.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.commons.domain.ActiveTrip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveTripManagerTest {

    @Mock private ActiveTripRepository activeTripRepository;

    @InjectMocks private ActiveTripManager activeTripManager;

    @Test
    void manageActiveTrip_whenInProgress_andNoExistingActiveTrip_shouldCreateActiveTrip() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        activeTripManager.manageActiveTrip(userId, tripId, TripStatus.IN_PROGRESS);

        // Then
        ArgumentCaptor<ActiveTrip> captor = ArgumentCaptor.forClass(ActiveTrip.class);
        verify(activeTripRepository).save(captor.capture());

        ActiveTrip saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTripId()).isEqualTo(tripId);
    }

    @Test
    void manageActiveTrip_whenInProgress_andExistingActiveTrip_shouldUpdateActiveTrip() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID oldTripId = UUID.randomUUID();
        UUID newTripId = UUID.randomUUID();
        ActiveTrip existing = ActiveTrip.builder().userId(userId).tripId(oldTripId).build();
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(existing));

        // When
        activeTripManager.manageActiveTrip(userId, newTripId, TripStatus.IN_PROGRESS);

        // Then
        ArgumentCaptor<ActiveTrip> captor = ArgumentCaptor.forClass(ActiveTrip.class);
        verify(activeTripRepository).save(captor.capture());

        ActiveTrip saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTripId()).isEqualTo(newTripId);
    }

    @Test
    void manageActiveTrip_whenPaused_andMatchingActiveTrip_shouldKeepActiveTrip() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));

        // When
        activeTripManager.manageActiveTrip(userId, tripId, TripStatus.PAUSED);

        // Then
        ArgumentCaptor<ActiveTrip> captor = ArgumentCaptor.forClass(ActiveTrip.class);
        verify(activeTripRepository).save(captor.capture());

        ActiveTrip saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTripId()).isEqualTo(tripId);
        verify(activeTripRepository, never()).delete(any(ActiveTrip.class));
    }

    @Test
    void manageActiveTrip_whenFinished_andMatchingActiveTrip_shouldDeleteActiveTrip() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));

        // When
        activeTripManager.manageActiveTrip(userId, tripId, TripStatus.FINISHED);

        // Then
        verify(activeTripRepository).delete(activeTrip);
    }

    @Test
    void manageActiveTrip_whenResting_andMatchingActiveTrip_shouldKeepActiveTrip() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(tripId).build();
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));

        // When
        activeTripManager.manageActiveTrip(userId, tripId, TripStatus.RESTING);

        // Then
        ArgumentCaptor<ActiveTrip> captor = ArgumentCaptor.forClass(ActiveTrip.class);
        verify(activeTripRepository).save(captor.capture());

        ActiveTrip saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTripId()).isEqualTo(tripId);
        verify(activeTripRepository, never()).delete(any(ActiveTrip.class));
    }

    @Test
    void manageActiveTrip_whenFinished_andNoActiveTrip_shouldNotDelete() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        when(activeTripRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        activeTripManager.manageActiveTrip(userId, tripId, TripStatus.FINISHED);

        // Then
        verify(activeTripRepository, never()).delete(any(ActiveTrip.class));
        verify(activeTripRepository, never()).save(any(ActiveTrip.class));
    }

    @Test
    void manageActiveTrip_whenFinished_andDifferentTripActive_shouldNotDelete() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID otherTripId = UUID.randomUUID();
        ActiveTrip activeTrip = ActiveTrip.builder().userId(userId).tripId(otherTripId).build();
        when(activeTripRepository.findById(userId)).thenReturn(Optional.of(activeTrip));

        // When
        activeTripManager.manageActiveTrip(userId, tripId, TripStatus.FINISHED);

        // Then
        verify(activeTripRepository, never()).delete(any(ActiveTrip.class));
        verify(activeTripRepository, never()).save(any(ActiveTrip.class));
    }
}
