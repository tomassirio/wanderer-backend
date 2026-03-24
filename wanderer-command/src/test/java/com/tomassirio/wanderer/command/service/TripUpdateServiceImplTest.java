package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.impl.TripUpdateServiceImpl;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
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
class TripUpdateServiceImplTest {

    @Mock private TripRepository tripRepository;

    @Mock private TripUpdateRepository tripUpdateRepository;

    @Mock private OwnershipValidator ownershipValidator;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private GeocodingService geocodingService;

    @Mock private WeatherService weatherService;

    @Mock private DistanceCalculationStrategy distanceCalculationStrategy;

    @InjectMocks private TripUpdateServiceImpl tripUpdateService;

    @Test
    void createTripUpdate_whenGeocodingSucceeds_shouldIncludeCityCountryInEvent() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(48.8566).lon(2.3522).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 85, "Paris!", null);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Euro Trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location))
                .thenReturn(new GeocodingService.GeocodingResult("Paris", "France"));
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getCity()).isEqualTo("Paris");
        assertThat(event.getCountry()).isEqualTo("France");
        assertThat(event.getLocation()).isEqualTo(location);
        assertThat(event.getBatteryLevel()).isEqualTo(85);
        assertThat(event.getMessage()).isEqualTo("Paris!");
    }

    @Test
    void createTripUpdate_whenGeocodingReturnsNull_shouldPublishEventWithNullCityCountry() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(0.0).lon(0.0).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 50, "Ocean", null);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Ocean Trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location)).thenReturn(null);
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getCity()).isNull();
        assertThat(event.getCountry()).isNull();
    }

    @Test
    void createTripUpdate_whenWeatherSucceeds_shouldIncludeWeatherInEvent() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 90, "Santiago!", null);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Camino").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location))
                .thenReturn(
                        new GeocodingService.GeocodingResult("Santiago de Compostela", "Spain"));
        when(weatherService.lookupCurrentWeather(location))
                .thenReturn(new WeatherService.WeatherResult(18.5, WeatherCondition.PARTLY_CLOUDY));

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getCity()).isEqualTo("Santiago de Compostela");
        assertThat(event.getCountry()).isEqualTo("Spain");
        assertThat(event.getTemperatureCelsius()).isEqualTo(18.5);
        assertThat(event.getWeatherCondition()).isEqualTo(WeatherCondition.PARTLY_CLOUDY);
    }

    @Test
    void createTripUpdate_whenWeatherReturnsNull_shouldPublishEventWithNullWeather() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(48.8566).lon(2.3522).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 75, "Rainy?", null);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Euro Trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location))
                .thenReturn(new GeocodingService.GeocodingResult("Paris", "France"));
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getTemperatureCelsius()).isNull();
        assertThat(event.getWeatherCondition()).isNull();
    }

    @Test
    void createTripUpdate_whenUpdateTypeIsDayStart_shouldIncludeUpdateTypeInEvent() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 100, "Good morning!", UpdateType.DAY_START);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Camino").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location)).thenReturn(null);
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getUpdateType()).isEqualTo(UpdateType.DAY_START);
    }

    @Test
    void createTripUpdate_whenUpdateTypeIsDayEnd_shouldIncludeUpdateTypeInEvent() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 20, "Good night!", UpdateType.DAY_END);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Camino").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location)).thenReturn(null);
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getUpdateType()).isEqualTo(UpdateType.DAY_END);
    }

    @Test
    void createTripUpdate_whenUpdateTypeIsTripStarted_shouldIncludeUpdateTypeInEvent() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 100, "Let's go!", UpdateType.TRIP_STARTED);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Camino").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location)).thenReturn(null);
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getUpdateType()).isEqualTo(UpdateType.TRIP_STARTED);
    }

    @Test
    void createTripUpdate_whenUpdateTypeIsTripEnded_shouldIncludeUpdateTypeInEvent() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(42.8805).lon(-8.5457).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 15, "We made it!", UpdateType.TRIP_ENDED);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Camino").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location)).thenReturn(null);
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getUpdateType()).isEqualTo(UpdateType.TRIP_ENDED);
    }

    @Test
    void createTripUpdate_whenUpdateTypeIsNull_shouldPublishEventWithNullUpdateType() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        GeoLocation location = GeoLocation.builder().lat(48.8566).lon(2.3522).build();
        TripUpdateCreationRequest request =
                new TripUpdateCreationRequest(location, 75, "Walking", null);

        Trip trip = Trip.builder().id(tripId).userId(userId).name("Euro Trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        doNothing().when(ownershipValidator).validateOwnership(any(), any(), any(), any(), any());
        when(geocodingService.reverseGeocode(location)).thenReturn(null);
        when(weatherService.lookupCurrentWeather(location)).thenReturn(null);

        // When
        UUID result = tripUpdateService.createTripUpdate(userId, tripId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TripUpdatedEvent event = captor.getValue();
        assertThat(event.getUpdateType()).isNull();
    }
}
