package com.tomassirio.wanderer.command.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.google.maps.GeoApiContext;
import com.tomassirio.wanderer.command.WandererCommandApplication;
import com.tomassirio.wanderer.command.client.WandererAuthClient;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.command.service.ThumbnailBackfillService;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.User;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proves the backfill draws each thumbnail with no open DB transaction, and that the detached trip
 * it hands over has its updates initialised (no LazyInitializationException).
 */
@SpringBootTest(classes = WandererCommandApplication.class)
@Import(TestConfig.class)
@TestPropertySource(
        properties = {
            "jwt.secret=test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum",
            "wanderer.auth.url=http://localhost:8083",
            "spring.cloud.compatibility-verifier.enabled=false"
        })
class ThumbnailBackfillServiceIntegrationTest extends BaseIntegrationTest {

    @MockitoBean private WandererAuthClient wandererAuthClient;
    @MockitoBean private ThumbnailService thumbnailService;
    @MockitoBean private GeoApiContext geoApiContext;
    @MockitoBean private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired private ThumbnailBackfillService backfillService;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripUpdateRepository tripUpdateRepository;

    @Test
    void generatesOutsideTransactionWithUpdatesLoaded() {
        User user =
                userRepository.save(
                        User.builder()
                                .id(UUID.randomUUID())
                                .username("backfill-" + UUID.randomUUID())
                                .build());
        Trip trip =
                tripRepository.save(
                        Trip.builder()
                                .id(UUID.randomUUID())
                                .name("Backfill trip")
                                .userId(user.getId())
                                .tripSettings(
                                        TripSettings.builder()
                                                .tripStatus(TripStatus.IN_PROGRESS)
                                                .visibility(TripVisibility.PUBLIC)
                                                .build())
                                .creationTimestamp(Instant.now())
                                .enabled(true)
                                .build());
        tripUpdateRepository.save(
                TripUpdate.builder()
                        .id(UUID.randomUUID())
                        .trip(trip)
                        .location(GeoLocation.builder().lat(42.88).lon(-8.54).build())
                        .timestamp(Instant.now())
                        .build());

        AtomicBoolean txActive = new AtomicBoolean(true);
        AtomicInteger updatesSeen = new AtomicInteger(-1);
        AtomicBoolean generated = new AtomicBoolean(false);
        when(thumbnailService.thumbnailExists(any(), eq(ThumbnailEntityType.TRIP)))
                .thenAnswer(inv -> inv.getArgument(0).equals(trip.getId()) && generated.get());
        doAnswer(
                        inv -> {
                            Trip t = inv.getArgument(0);
                            if (t.getId().equals(trip.getId())) {
                                txActive.set(
                                        TransactionSynchronizationManager
                                                .isActualTransactionActive());
                                updatesSeen.set(t.getTripUpdates().size());
                                generated.set(true);
                            }
                            return null;
                        })
                .when(thumbnailService)
                .generateAndSaveThumbnail(any(Trip.class));

        backfillService.regenerateMissingTripThumbnails();

        assertThat(generated).isTrue();
        assertThat(txActive).isFalse();
        assertThat(updatesSeen.get()).isEqualTo(1);
    }
}
