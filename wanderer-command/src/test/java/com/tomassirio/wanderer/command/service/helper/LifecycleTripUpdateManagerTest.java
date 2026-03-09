package com.tomassirio.wanderer.command.service.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LifecycleTripUpdateManagerTest {

    @Mock private TripUpdateRepository tripUpdateRepository;

    @InjectMocks private LifecycleTripUpdateManager lifecycleTripUpdateManager;

    // --- createLifecycleTripUpdate tests ---

    @Test
    void createLifecycleTripUpdate_whenCreatedToInProgress_shouldSaveTripStartedUpdate() {
        // Given
        Trip trip = createTrip();

        // When
        lifecycleTripUpdateManager.createLifecycleTripUpdate(
                trip, TripStatus.CREATED, TripStatus.IN_PROGRESS);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.TRIP_STARTED);
        assertThat(saved.getTrip()).isEqualTo(trip);
        assertThat(saved.getLocation()).isNull();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void createLifecycleTripUpdate_whenInProgressToFinished_shouldSaveTripEndedUpdate() {
        // Given
        Trip trip = createTrip();

        // When
        lifecycleTripUpdateManager.createLifecycleTripUpdate(
                trip, TripStatus.IN_PROGRESS, TripStatus.FINISHED);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.TRIP_ENDED);
    }

    @Test
    void createLifecycleTripUpdate_whenInProgressToResting_shouldSaveDayEndUpdate() {
        // Given
        Trip trip = createTrip();

        // When
        lifecycleTripUpdateManager.createLifecycleTripUpdate(
                trip, TripStatus.IN_PROGRESS, TripStatus.RESTING);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.DAY_END);
    }

    @Test
    void createLifecycleTripUpdate_whenRestingToInProgress_shouldSaveDayStartUpdate() {
        // Given
        Trip trip = createTrip();

        // When
        lifecycleTripUpdateManager.createLifecycleTripUpdate(
                trip, TripStatus.RESTING, TripStatus.IN_PROGRESS);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.DAY_START);
    }

    @Test
    void createLifecycleTripUpdate_whenInProgressToPaused_shouldNotSave() {
        // Given
        Trip trip = createTrip();

        // When
        lifecycleTripUpdateManager.createLifecycleTripUpdate(
                trip, TripStatus.IN_PROGRESS, TripStatus.PAUSED);

        // Then
        verify(tripUpdateRepository, never()).save(any(TripUpdate.class));
    }

    @Test
    void createLifecycleTripUpdate_whenPausedToInProgress_shouldNotSave() {
        // Given
        Trip trip = createTrip();

        // When
        lifecycleTripUpdateManager.createLifecycleTripUpdate(
                trip, TripStatus.PAUSED, TripStatus.IN_PROGRESS);

        // Then
        verify(tripUpdateRepository, never()).save(any(TripUpdate.class));
    }


    // --- Helper methods ---

    private Trip createTrip() {
        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.CREATED)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        TripDetails tripDetails = TripDetails.builder().build();
        return Trip.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tripSettings(tripSettings)
                .tripDetails(tripDetails)
                .build();
    }
}

