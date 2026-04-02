package com.tomassirio.wanderer.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.AchievementService;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
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
class TripUpdatedEventHandlerTest {

    @Mock private TripRepository tripRepository;

    @Mock private TripUpdateRepository tripUpdateRepository;

    @Mock private AchievementService achievementCalculationService;

    @InjectMocks private TripUpdatedEventHandler handler;

    @Test
    void handle_shouldPersistTripUpdate() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripUpdateId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        Instant timestamp = Instant.now();

        Trip trip = Trip.builder().id(tripId).name("Camino").build();

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(85)
                        .message("Arrived at Santiago!")
                        .city("Santiago de Compostela")
                        .country("Spain")
                        .temperatureCelsius(18.5)
                        .weatherCondition(WeatherCondition.PARTLY_CLOUDY)
                        .timestamp(timestamp)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> updateCaptor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(updateCaptor.capture());

        TripUpdate saved = updateCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(tripUpdateId);
        assertThat(saved.getTrip()).isEqualTo(trip);
        assertThat(saved.getLocation()).isEqualTo(location);
        assertThat(saved.getBattery()).isEqualTo(85);
        assertThat(saved.getMessage()).isEqualTo("Arrived at Santiago!");
        assertThat(saved.getCity()).isEqualTo("Santiago de Compostela");
        assertThat(saved.getCountry()).isEqualTo("Spain");
        assertThat(saved.getTemperatureCelsius()).isEqualTo(18.5);
        assertThat(saved.getWeatherCondition()).isEqualTo(WeatherCondition.PARTLY_CLOUDY);
        assertThat(saved.getTimestamp()).isEqualTo(timestamp);

        // Verify updateCount was incremented and trip saved
        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(tripCaptor.capture());
        assertThat(tripCaptor.getValue().getUpdateCount()).isEqualTo(1);

        // Verify achievement calculation was triggered
        verify(achievementCalculationService).checkAndUnlockAchievements(tripId);
    }

    @Test
    void handle_whenGeocodingReturnsNull_shouldPersistWithoutCityCountry() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripUpdateId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(0.0).lon(0.0).build();
        Instant timestamp = Instant.now();

        Trip trip = Trip.builder().id(tripId).name("Camino").build();

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(50)
                        .message("In the middle of nowhere")
                        .timestamp(timestamp)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(tripUpdateId);
        assertThat(saved.getCity()).isNull();
        assertThat(saved.getCountry()).isNull();

        verify(achievementCalculationService).checkAndUnlockAchievements(tripId);
    }

    @Test
    void handle_whenUpdateTypeIsDayStart_shouldPersistUpdateType() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripUpdateId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        Instant timestamp = Instant.now();

        Trip trip = Trip.builder().id(tripId).name("Camino").build();

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(100)
                        .message("Good morning!")
                        .updateType(UpdateType.DAY_START)
                        .timestamp(timestamp)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.DAY_START);

        verify(achievementCalculationService).checkAndUnlockAchievements(tripId);
    }

    @Test
    void handle_whenUpdateTypeIsDayEnd_shouldPersistUpdateType() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripUpdateId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        Instant timestamp = Instant.now();

        Trip trip = Trip.builder().id(tripId).name("Camino").build();

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(20)
                        .message("Good night!")
                        .updateType(UpdateType.DAY_END)
                        .timestamp(timestamp)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.DAY_END);

        verify(achievementCalculationService).checkAndUnlockAchievements(tripId);
    }

    @Test
    void handle_whenUpdateTypeIsTripStarted_shouldPersistUpdateType() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripUpdateId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        Instant timestamp = Instant.now();

        Trip trip = Trip.builder().id(tripId).name("Camino").build();

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(100)
                        .message("Let's go!")
                        .updateType(UpdateType.TRIP_STARTED)
                        .timestamp(timestamp)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.TRIP_STARTED);

        verify(achievementCalculationService).checkAndUnlockAchievements(tripId);
    }

    @Test
    void handle_whenUpdateTypeIsTripEnded_shouldPersistUpdateType() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID tripUpdateId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        Instant timestamp = Instant.now();

        Trip trip = Trip.builder().id(tripId).name("Camino").build();

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(tripUpdateId)
                        .tripId(tripId)
                        .location(location)
                        .batteryLevel(15)
                        .message("We made it!")
                        .updateType(UpdateType.TRIP_ENDED)
                        .timestamp(timestamp)
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        // When
        handler.handle(event);

        // Then
        ArgumentCaptor<TripUpdate> captor = ArgumentCaptor.forClass(TripUpdate.class);
        verify(tripUpdateRepository).save(captor.capture());

        TripUpdate saved = captor.getValue();
        assertThat(saved.getUpdateType()).isEqualTo(UpdateType.TRIP_ENDED);

        verify(achievementCalculationService).checkAndUnlockAchievements(tripId);
    }
}
