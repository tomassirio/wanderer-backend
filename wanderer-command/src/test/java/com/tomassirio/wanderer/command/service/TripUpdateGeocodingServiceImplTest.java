package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.TripUpdateEnrichedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.impl.TripUpdateGeocodingServiceImpl;
import com.tomassirio.wanderer.command.utils.TestEntityFactory;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TripUpdateGeocodingServiceImplTest {

    @Mock private TripRepository tripRepository;

    @Mock private TripUpdateRepository tripUpdateRepository;

    @Mock private GeocodingService geocodingService;

    @Mock private WeatherService weatherService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TripUpdateGeocodingServiceImpl tripUpdateGeocodingService;

    @Captor private ArgumentCaptor<List<TripUpdate>> updatesCaptor;

    @Test
    void recomputeGeocoding_whenTripNotFound_shouldThrowEntityNotFoundException() {
        // Given
        UUID tripId = UUID.randomUUID();
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripUpdateGeocodingService.recomputeGeocoding(tripId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Trip not found");

        verify(tripUpdateRepository, never()).findByTripIdOrderByTimestampAsc(any());
    }

    @Test
    void recomputeGeocoding_whenNoUpdates_shouldSaveEmptyList() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(Collections.emptyList());

        // When
        tripUpdateGeocodingService.recomputeGeocoding(tripId);

        // Then
        verify(tripUpdateRepository).saveAll(updatesCaptor.capture());
        assertThat(updatesCaptor.getValue()).isEmpty();
    }

    @Test
    void recomputeGeocoding_whenGeocodingSucceeds_shouldUpdateCityAndCountry() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        TripUpdate update = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update));
        when(geocodingService.reverseGeocode(update.getLocation()))
                .thenReturn(new GeocodingService.GeocodingResult("León", "Spain"));

        // When
        tripUpdateGeocodingService.recomputeGeocoding(tripId);

        // Then
        verify(tripUpdateRepository).saveAll(updatesCaptor.capture());
        List<TripUpdate> saved = updatesCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getCity()).isEqualTo("León");
        assertThat(saved.get(0).getCountry()).isEqualTo("Spain");
    }

    @Test
    void recomputeGeocoding_whenGeocodingReturnsNull_shouldClearCityAndCountry() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        TripUpdate update = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        update.setCity("Old City");
        update.setCountry("Old Country");

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update));
        when(geocodingService.reverseGeocode(update.getLocation())).thenReturn(null);

        // When
        tripUpdateGeocodingService.recomputeGeocoding(tripId);

        // Then
        verify(tripUpdateRepository).saveAll(updatesCaptor.capture());
        List<TripUpdate> saved = updatesCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getCity()).isNull();
        assertThat(saved.get(0).getCountry()).isNull();
    }

    @Test
    void recomputeGeocoding_withMultipleUpdates_shouldGeocodeEachOne() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        TripUpdate update1 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        TripUpdate update2 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);

        // Give each update a distinct location so Mockito can distinguish the stubs
        GeoLocation loc1 = GeoLocation.builder().lat(42.8125).lon(-1.6458).build();
        GeoLocation loc2 = GeoLocation.builder().lat(42.3440).lon(-3.6969).build();
        update1.setLocation(loc1);
        update2.setLocation(loc2);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update1, update2));
        when(geocodingService.reverseGeocode(loc1))
                .thenReturn(new GeocodingService.GeocodingResult("Pamplona", "Spain"));
        when(geocodingService.reverseGeocode(loc2))
                .thenReturn(new GeocodingService.GeocodingResult("Burgos", "Spain"));

        // When
        tripUpdateGeocodingService.recomputeGeocoding(tripId);

        // Then
        verify(tripUpdateRepository).saveAll(updatesCaptor.capture());
        List<TripUpdate> saved = updatesCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getCity()).isEqualTo("Pamplona");
        assertThat(saved.get(1).getCity()).isEqualTo("Burgos");
    }

    @Test
    void recomputeGeocoding_shouldNotTouchOtherFields() {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID updateId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        TripUpdate update = TestEntityFactory.createTripUpdate(updateId, trip);
        String originalMessage = update.getMessage();
        Integer originalBattery = update.getBattery();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update));
        when(geocodingService.reverseGeocode(update.getLocation()))
                .thenReturn(new GeocodingService.GeocodingResult("Santiago", "Spain"));

        // When
        tripUpdateGeocodingService.recomputeGeocoding(tripId);

        // Then
        verify(tripUpdateRepository).saveAll(updatesCaptor.capture());
        TripUpdate saved = updatesCaptor.getValue().get(0);
        assertThat(saved.getId()).isEqualTo(updateId);
        assertThat(saved.getMessage()).isEqualTo(originalMessage);
        assertThat(saved.getBattery()).isEqualTo(originalBattery);
        assertThat(saved.getCity()).isEqualTo("Santiago");
        assertThat(saved.getCountry()).isEqualTo("Spain");
    }

    // --- enrichTripUpdate ---

    private static final GeoLocation HERE = new GeoLocation(52.0907, 5.1214);
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private TripUpdate checkIn(UUID tripId, GeoLocation location, Instant timestamp) {
        return TripUpdate.builder()
                .id(UUID.randomUUID())
                .trip(Trip.builder().id(tripId).build())
                .location(location)
                .timestamp(timestamp)
                .build();
    }

    private TripUpdate enriched(UUID tripId, GeoLocation location, Instant timestamp) {
        TripUpdate previous = checkIn(tripId, location, timestamp);
        previous.setCity("Utrecht");
        previous.setCountry("Netherlands");
        previous.setTemperatureCelsius(12.0);
        previous.setWeatherCondition(WeatherCondition.CLOUDY);
        return previous;
    }

    private void givenPrevious(TripUpdate current, TripUpdate previous) {
        when(tripUpdateRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(tripUpdateRepository
                        .findFirstByTripIdAndIdNotAndCityIsNotNullAndTimestampLessThanEqualOrderByTimestampDesc(
                                current.getTrip().getId(), current.getId(), current.getTimestamp()))
                .thenReturn(Optional.ofNullable(previous));
    }

    @Test
    void enrichTripUpdate_withoutNearbyPrevious_looksUpAndBroadcasts() {
        UUID tripId = UUID.randomUUID();
        TripUpdate current = checkIn(tripId, HERE, NOW);
        givenPrevious(current, null);
        when(geocodingService.reverseGeocode(HERE))
                .thenReturn(new GeocodingService.GeocodingResult("Utrecht", "Netherlands"));
        when(weatherService.lookupCurrentWeather(HERE))
                .thenReturn(new WeatherService.WeatherResult(14.5, WeatherCondition.CLEAR));

        tripUpdateGeocodingService.enrichTripUpdate(current.getId());

        assertThat(current.getCity()).isEqualTo("Utrecht");
        assertThat(current.getTemperatureCelsius()).isEqualTo(14.5);
        verify(tripUpdateRepository).save(current);
        ArgumentCaptor<TripUpdateEnrichedEvent> captor =
                ArgumentCaptor.forClass(TripUpdateEnrichedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TripUpdateEnrichedEvent event = captor.getValue();
        assertThat(event.getTripId()).isEqualTo(tripId);
        assertThat(event.getTripUpdateId()).isEqualTo(current.getId());
        assertThat(event.getCountry()).isEqualTo("Netherlands");
        assertThat(event.getWeatherCondition()).isEqualTo(WeatherCondition.CLEAR);
        assertThat(event.getEventType()).isEqualTo("TRIP_UPDATE_ENRICHED");
    }

    @Test
    void enrichTripUpdate_whenPreviousWithin300mAnd30min_copiesWithoutLookups() {
        UUID tripId = UUID.randomUUID();
        TripUpdate current = checkIn(tripId, HERE, NOW);
        // ~110 m north, 20 minutes earlier
        givenPrevious(
                current,
                enriched(
                        tripId,
                        new GeoLocation(HERE.getLat() - 0.001, HERE.getLon()),
                        NOW.minus(Duration.ofMinutes(20))));

        tripUpdateGeocodingService.enrichTripUpdate(current.getId());

        verifyNoInteractions(geocodingService, weatherService);
        assertThat(current.getCity()).isEqualTo("Utrecht");
        assertThat(current.getCountry()).isEqualTo("Netherlands");
        assertThat(current.getTemperatureCelsius()).isEqualTo(12.0);
        assertThat(current.getWeatherCondition()).isEqualTo(WeatherCondition.CLOUDY);
        verify(eventPublisher).publishEvent(any(TripUpdateEnrichedEvent.class));
    }

    @Test
    void enrichTripUpdate_whenPreviousTooFar_looksUp() {
        UUID tripId = UUID.randomUUID();
        TripUpdate current = checkIn(tripId, HERE, NOW);
        // ~1.1 km away
        givenPrevious(
                current,
                enriched(
                        tripId,
                        new GeoLocation(HERE.getLat() - 0.01, HERE.getLon()),
                        NOW.minus(Duration.ofMinutes(5))));

        tripUpdateGeocodingService.enrichTripUpdate(current.getId());

        verify(geocodingService).reverseGeocode(HERE);
        verify(weatherService).lookupCurrentWeather(HERE);
    }

    @Test
    void enrichTripUpdate_whenPreviousTooOld_looksUp() {
        UUID tripId = UUID.randomUUID();
        TripUpdate current = checkIn(tripId, HERE, NOW);
        givenPrevious(current, enriched(tripId, HERE, NOW.minus(Duration.ofMinutes(31))));

        tripUpdateGeocodingService.enrichTripUpdate(current.getId());

        verify(geocodingService).reverseGeocode(HERE);
        verify(weatherService).lookupCurrentWeather(HERE);
    }

    @Test
    void enrichTripUpdate_whenLookupsReturnNothing_doesNotBroadcast() {
        UUID tripId = UUID.randomUUID();
        TripUpdate current = checkIn(tripId, HERE, NOW);
        givenPrevious(current, null);

        tripUpdateGeocodingService.enrichTripUpdate(current.getId());

        verify(tripUpdateRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void enrichTripUpdate_withoutLocation_doesNothing() {
        UUID tripId = UUID.randomUUID();
        TripUpdate current = checkIn(tripId, null, NOW);
        when(tripUpdateRepository.findById(current.getId())).thenReturn(Optional.of(current));

        tripUpdateGeocodingService.enrichTripUpdate(current.getId());

        verifyNoInteractions(geocodingService, weatherService, eventPublisher);
    }
}
