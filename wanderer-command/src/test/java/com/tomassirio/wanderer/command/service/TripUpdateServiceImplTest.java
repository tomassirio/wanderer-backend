package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.impl.TripUpdateServiceImpl;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TripUpdateServiceImplTest {

    private static final GeoLocation SANTIAGO =
            GeoLocation.builder().lat(42.8805).lon(-8.5457).build();

    @Mock private TripRepository tripRepository;

    @Mock private TripUpdateRepository tripUpdateRepository;

    @Mock private OwnershipValidator ownershipValidator;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private DistanceCalculationStrategy distanceCalculationStrategy;

    @InjectMocks private TripUpdateServiceImpl tripUpdateService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        givenTripInStatus(TripStatus.IN_PROGRESS);
    }

    @Test
    void createTripUpdate_publishesEventWithoutCallingExternalApis() {
        UUID result =
                tripUpdateService.createTripUpdate(
                        userId, tripId, new TripUpdateCreationRequest(SANTIAGO, 85, "Hi!", null));

        TripUpdatedEvent event = publishedEvent();
        assertThat(result).isEqualTo(event.getTripUpdateId());
        assertThat(event.getLocation()).isEqualTo(SANTIAGO);
        assertThat(event.getBatteryLevel()).isEqualTo(85);
        assertThat(event.getMessage()).isEqualTo("Hi!");
        assertThat(event.getUpdateType()).isNull();
        assertThat(event.getTimestamp()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        assertThat(event.toWebSocketPayload())
                .hasFieldOrPropertyWithValue("tripUpdateId", result)
                .hasFieldOrPropertyWithValue("timestamp", event.getTimestamp());
    }

    @ParameterizedTest
    @EnumSource(UpdateType.class)
    void createTripUpdate_carriesUpdateType(UpdateType type) {
        tripUpdateService.createTripUpdate(
                userId, tripId, new TripUpdateCreationRequest(SANTIAGO, 100, null, type));

        assertThat(publishedEvent().getUpdateType()).isEqualTo(type);
    }

    @Test
    void createTripUpdate_withClientIdAndRecordedAt_usesThem() {
        UUID clientId = UUID.randomUUID();
        Instant recordedAt = Instant.now().minus(Duration.ofHours(2));
        when(tripUpdateRepository.existsById(clientId)).thenReturn(false);

        UUID result =
                tripUpdateService.createTripUpdate(
                        userId,
                        tripId,
                        new TripUpdateCreationRequest(
                                SANTIAGO, null, null, null, clientId, recordedAt));

        assertThat(result).isEqualTo(clientId);
        TripUpdatedEvent event = publishedEvent();
        assertThat(event.getTripUpdateId()).isEqualTo(clientId);
        assertThat(event.getTimestamp()).isEqualTo(recordedAt);
    }

    @Test
    void createTripUpdate_whenClientIdAlreadyExists_returnsItWithoutPublishing() {
        UUID clientId = UUID.randomUUID();
        when(tripUpdateRepository.existsById(clientId)).thenReturn(true);
        // Even after the trip ended, a retry of an already-stored check-in is a no-op success.
        givenTripInStatus(TripStatus.FINISHED);

        UUID result =
                tripUpdateService.createTripUpdate(
                        userId,
                        tripId,
                        new TripUpdateCreationRequest(SANTIAGO, null, null, null, clientId, null));

        assertThat(result).isEqualTo(clientId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createTripUpdate_whenRecordedAtFarInFuture_isRejected() {
        Instant future = Instant.now().plus(Duration.ofMinutes(10));

        assertThatThrownBy(
                        () ->
                                tripUpdateService.createTripUpdate(
                                        userId,
                                        tripId,
                                        new TripUpdateCreationRequest(
                                                SANTIAGO, null, null, null, null, future)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createTripUpdate_whenRecordedAtSlightlyAhead_isAccepted() {
        Instant skewed = Instant.now().plus(Duration.ofMinutes(2));

        tripUpdateService.createTripUpdate(
                userId,
                tripId,
                new TripUpdateCreationRequest(SANTIAGO, null, null, null, null, skewed));

        assertThat(publishedEvent().getTimestamp()).isEqualTo(skewed);
    }

    @Test
    void createTripUpdate_regularWithoutLocation_isRejected() {
        assertThatThrownBy(
                        () ->
                                tripUpdateService.createTripUpdate(
                                        userId,
                                        tripId,
                                        new TripUpdateCreationRequest(
                                                null, 50, "x", UpdateType.REGULAR)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                tripUpdateService.createTripUpdate(
                                        userId,
                                        tripId,
                                        new TripUpdateCreationRequest(null, 50, "x", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(
            value = UpdateType.class,
            names = {"DAY_START", "DAY_END", "TRIP_STARTED", "TRIP_ENDED"})
    void createTripUpdate_lifecycleMarkerWithoutLocation_isAccepted(UpdateType type) {
        tripUpdateService.createTripUpdate(
                userId, tripId, new TripUpdateCreationRequest(null, 50, null, type));

        TripUpdatedEvent event = publishedEvent();
        assertThat(event.getLocation()).isNull();
        assertThat(event.getUpdateType()).isEqualTo(type);
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED, REGULAR",
        "CREATED, TRIP_STARTED",
        "RESTING, REGULAR",
        "FINISHED, REGULAR",
        "FINISHED, DAY_END"
    })
    void createTripUpdate_whenTripDoesNotAcceptCheckIn_shouldRejectWithoutPublishing(
            TripStatus status, UpdateType type) {
        givenTripInStatus(status);

        assertThatThrownBy(
                        () ->
                                tripUpdateService.createTripUpdate(
                                        userId,
                                        tripId,
                                        new TripUpdateCreationRequest(SANTIAGO, 50, null, type)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(status.name());
        verifyNoInteractions(eventPublisher);
    }

    private void givenTripInStatus(TripStatus status) {
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(userId)
                        .name("Camino")
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(status)
                                        .visibility(TripVisibility.PUBLIC)
                                        .build())
                        .build();
        lenient().when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    }

    private TripUpdatedEvent publishedEvent() {
        ArgumentCaptor<TripUpdatedEvent> captor = ArgumentCaptor.forClass(TripUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
