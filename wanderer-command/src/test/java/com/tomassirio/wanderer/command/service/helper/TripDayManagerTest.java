package com.tomassirio.wanderer.command.service.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.repository.TripDayRepository;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDay;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
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
class TripDayManagerTest {

    @Mock private TripDayRepository tripDayRepository;

    @InjectMocks private TripDayManager tripDayManager;

    @Test
    void manageTripDays_whenCreatedToInProgress_shouldCreateDay1() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.CREATED);

        // When
        tripDayManager.manageTripDays(trip, TripStatus.CREATED, TripStatus.IN_PROGRESS);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getDayNumber()).isEqualTo(1);
        assertThat(savedDay.getStartTimestamp()).isNotNull();
        assertThat(savedDay.getEndTimestamp()).isNull();
        assertThat(savedDay.getTrip()).isEqualTo(trip);
        assertThat(trip.getTripDetails().getCurrentDay()).isEqualTo(1);
    }

    @Test
    void manageTripDays_whenInProgressToResting_shouldCloseCurrentDay() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.IN_PROGRESS);
        trip.getTripDetails().setCurrentDay(1);

        TripDay openDay =
                TripDay.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .dayNumber(1)
                        .startTimestamp(Instant.now().minusSeconds(3600))
                        .build();

        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(trip.getId()))
                .thenReturn(Optional.of(openDay));

        // When
        tripDayManager.manageTripDays(trip, TripStatus.IN_PROGRESS, TripStatus.RESTING);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getEndTimestamp()).isNotNull();
        assertThat(savedDay.getDayNumber()).isEqualTo(1);
    }

    @Test
    void manageTripDays_whenInProgressToResting_noOpenDay_shouldNotSave() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.IN_PROGRESS);
        trip.getTripDetails().setCurrentDay(1);

        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(trip.getId()))
                .thenReturn(Optional.empty());

        // When
        tripDayManager.manageTripDays(trip, TripStatus.IN_PROGRESS, TripStatus.RESTING);

        // Then
        verify(tripDayRepository, never()).save(any(TripDay.class));
    }

    @Test
    void manageTripDays_whenRestingToInProgress_shouldCreateNextDay() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.RESTING);
        trip.getTripDetails().setCurrentDay(1);

        // When
        tripDayManager.manageTripDays(trip, TripStatus.RESTING, TripStatus.IN_PROGRESS);

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
    void manageTripDays_whenRestingToInProgress_withNullCurrentDay_shouldCreateDay1() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.RESTING);
        trip.getTripDetails().setCurrentDay(null);

        // When
        tripDayManager.manageTripDays(trip, TripStatus.RESTING, TripStatus.IN_PROGRESS);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getDayNumber()).isEqualTo(1);
        assertThat(trip.getTripDetails().getCurrentDay()).isEqualTo(1);
    }

    @Test
    void manageTripDays_whenFinished_shouldCloseOpenDay() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.IN_PROGRESS);
        trip.getTripDetails().setCurrentDay(3);

        TripDay openDay =
                TripDay.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .dayNumber(3)
                        .startTimestamp(Instant.now().minusSeconds(3600))
                        .build();

        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(trip.getId()))
                .thenReturn(Optional.of(openDay));

        // When
        tripDayManager.manageTripDays(trip, TripStatus.IN_PROGRESS, TripStatus.FINISHED);

        // Then
        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayRepository).save(dayCaptor.capture());

        TripDay savedDay = dayCaptor.getValue();
        assertThat(savedDay.getEndTimestamp()).isNotNull();
        assertThat(savedDay.getDayNumber()).isEqualTo(3);
    }

    @Test
    void manageTripDays_whenFinished_noOpenDay_shouldNotSave() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.IN_PROGRESS);
        trip.getTripDetails().setCurrentDay(1);

        when(tripDayRepository.findByTripIdAndEndTimestampIsNull(trip.getId()))
                .thenReturn(Optional.empty());

        // When
        tripDayManager.manageTripDays(trip, TripStatus.IN_PROGRESS, TripStatus.FINISHED);

        // Then
        verify(tripDayRepository, never()).save(any(TripDay.class));
    }

    @Test
    void manageTripDays_whenInProgressToPaused_shouldNotInteractWithRepository() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.IN_PROGRESS);

        // When
        tripDayManager.manageTripDays(trip, TripStatus.IN_PROGRESS, TripStatus.PAUSED);

        // Then
        verify(tripDayRepository, never()).save(any(TripDay.class));
        verify(tripDayRepository, never()).findByTripIdAndEndTimestampIsNull(any());
    }

    @Test
    void manageTripDays_whenPausedToInProgress_shouldNotInteractWithRepository() {
        // Given
        Trip trip = createTripWithDetails(TripStatus.PAUSED);

        // When
        tripDayManager.manageTripDays(trip, TripStatus.PAUSED, TripStatus.IN_PROGRESS);

        // Then
        verify(tripDayRepository, never()).save(any(TripDay.class));
        verify(tripDayRepository, never()).findByTripIdAndEndTimestampIsNull(any());
    }

    // --- Helper methods ---

    private Trip createTripWithDetails(TripStatus status) {
        TripSettings tripSettings =
                TripSettings.builder().tripStatus(status).visibility(TripVisibility.PUBLIC).build();
        TripDetails tripDetails = TripDetails.builder().build();
        return Trip.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tripSettings(tripSettings)
                .tripDetails(tripDetails)
                .build();
    }
}
