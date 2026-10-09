package com.tomassirio.wanderer.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.maps.GeoApiContext;
import com.tomassirio.wanderer.command.WandererCommandApplication;
import com.tomassirio.wanderer.command.client.WandererAuthClient;
import com.tomassirio.wanderer.command.controller.request.StartTripRequest;
import com.tomassirio.wanderer.command.controller.request.TrackPointsRequest;
import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripPlanCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.repository.ActiveTripRepository;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.DraftMigrationReportDTO;
import com.tomassirio.wanderer.commons.dto.StartTripResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** One-step trip start, check-in guard and Draft migration against a real Postgres. */
@SpringBootTest(classes = WandererCommandApplication.class)
@Import(TestConfig.class)
@TestPropertySource(
        properties = {
            "jwt.secret=test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum",
            "wanderer.auth.url=http://localhost:8083",
            "spring.cloud.compatibility-verifier.enabled=false"
        })
class TripStartIT extends BaseIntegrationTest {

    private static final GeoLocation HERE = new GeoLocation(52.09, 5.12);

    @MockitoBean private WandererAuthClient wandererAuthClient;
    @MockitoBean private GeocodingService geocodingService;
    @MockitoBean private WeatherService weatherService;
    @MockitoBean private AchievementService achievementService;
    @MockitoBean private ThumbnailService thumbnailService;
    @MockitoBean private TripPlanPolylineService tripPlanPolylineService;
    @MockitoBean private GeoApiContext geoApiContext;
    @MockitoBean private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired private TripService tripService;
    @Autowired private TrackPointService trackPointService;
    @Autowired private TripUpdateService tripUpdateService;
    @Autowired private DraftTripMigrationService draftTripMigrationService;
    @Autowired private TripPlanService tripPlanService;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripUpdateRepository tripUpdateRepository;
    @Autowired private TripPlanRepository tripPlanRepository;
    @Autowired private ActiveTripRepository activeTripRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        cleanUp();
        userId = UUID.randomUUID();
        userRepository.save(User.builder().id(userId).username("u" + userId).build());
        when(geocodingService.reverseGeocode(any()))
                .thenReturn(new GeocodingService.GeocodingResult("Utrecht", "Netherlands"));
    }

    @AfterEach
    void cleanUp() {
        activeTripRepository.deleteAll();
        tripRepository.deleteAll();
        tripPlanRepository.deleteAll();
        userRepository.deleteAll();
    }

    private StartTripRequest scratch(String name) {
        return new StartTripRequest(
                name, TripVisibility.PROTECTED, null, true, null, HERE, 80, null, null);
    }

    @Test
    void startTrip_createsLiveTripWithFirstCheckInInOneGo() {
        StartTripResponse response = tripService.startTrip(userId, "key-1", scratch("Camino"));

        assertThat(response.replayed()).isFalse();
        Trip trip = tripRepository.findById(response.tripId()).orElseThrow();
        assertThat(trip.getTripSettings().getTripStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(trip.getTripSettings().getVisibility()).isEqualTo(TripVisibility.PROTECTED);
        assertThat(trip.getTripSettings().getTripModality()).isEqualTo(TripModality.SIMPLE);
        assertThat(trip.getTripSettings().getUpdateRefresh()).isEqualTo(900);
        assertThat(trip.getTripDetails().getStartTimestamp()).isNotNull();
        assertThat(activeTripRepository.findById(userId)).isPresent();

        List<TripUpdate> updates =
                tripUpdateRepository.findByTripIdOrderByTimestampAsc(trip.getId());
        assertThat(updates).hasSize(1);
        TripUpdate first = updates.get(0);
        assertThat(first.getId()).isEqualTo(response.tripUpdateId());
        assertThat(first.getUpdateType()).isEqualTo(UpdateType.TRIP_STARTED);
        assertThat(first.getBattery()).isEqualTo(80);
        // Place name arrives asynchronously after commit
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertThat(
                                                tripUpdateRepository
                                                        .findById(first.getId())
                                                        .orElseThrow()
                                                        .getCity())
                                        .isEqualTo("Utrecht"));
    }

    @Test
    void startTrip_whenGeocodingFails_stillStartsTrip() {
        when(geocodingService.reverseGeocode(any())).thenThrow(new RuntimeException("boom"));

        StartTripResponse response = tripService.startTrip(userId, "key-1", scratch("Camino"));

        assertThat(tripRepository.findById(response.tripId())).isPresent();
        assertThat(tripUpdateRepository.findById(response.tripUpdateId())).isPresent();
    }

    @Test
    void startTrip_whenAnotherTripIsOngoing_rejectsAndSavesNothing() {
        tripService.startTrip(userId, "key-1", scratch("First"));

        assertThatThrownBy(() -> tripService.startTrip(userId, "key-2", scratch("Second")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(tripRepository.findAllByUserId(userId)).hasSize(1);
    }

    @Test
    void startTrip_retryWithSameKey_returnsOriginalTrip() {
        StartTripResponse first = tripService.startTrip(userId, "key-1", scratch("Camino"));
        StartTripResponse retry = tripService.startTrip(userId, "key-1", scratch("Camino"));

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.tripId()).isEqualTo(first.tripId());
        assertThat(retry.tripUpdateId()).isEqualTo(first.tripUpdateId());
        assertThat(retry.status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(tripRepository.findAllByUserId(userId)).hasSize(1);
        assertThat(tripUpdateRepository.countByTripId(first.tripId())).isEqualTo(1);
    }

    @Test
    void startTrip_fromPlan_takesNameRouteDatesAndType() {
        TripPlan plan =
                tripPlanRepository.save(
                        TripPlan.builder()
                                .id(UUID.randomUUID())
                                .name("Tuvi Trip Plan")
                                .planType(TripPlanType.MULTI_DAY)
                                .userId(userId)
                                .createdTimestamp(Instant.now())
                                .startDate(LocalDate.of(2026, 10, 1))
                                .endDate(LocalDate.of(2026, 10, 5))
                                .startLocation(HERE)
                                .endLocation(new GeoLocation(42.88, -8.54))
                                .waypoints(List.of(new GeoLocation(48.85, 2.35)))
                                .plannedPolyline("abc")
                                .build());
        StartTripRequest request =
                new StartTripRequest(
                        "ignored",
                        TripVisibility.PUBLIC,
                        TripModality.SIMPLE,
                        false,
                        null,
                        HERE,
                        null,
                        null,
                        plan.getId());

        StartTripResponse response = tripService.startTrip(userId, "key-1", request);

        Trip trip = tripRepository.findById(response.tripId()).orElseThrow();
        assertThat(trip.getName()).isEqualTo("Tuvi Trip Plan");
        assertThat(trip.getTripPlanId()).isEqualTo(plan.getId());
        assertThat(trip.getTripSettings().getTripModality()).isEqualTo(TripModality.MULTI_DAY);
        assertThat(trip.getTripSettings().getTripStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(trip.getTripDetails().getEndLocation().getLat()).isEqualTo(42.88);
        assertThat(trip.getTripDetails().getWaypoints()).hasSize(1);
        assertThat(trip.getTripDetails().getEndTimestamp())
                .isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
        assertThat(trip.getPlannedPolyline()).isEqualTo("abc");
        assertThat(tripUpdateRepository.countByTripId(trip.getId())).isEqualTo(1);
    }

    @Test
    void checkIn_onDraftTrip_isRejected() {
        UUID draftId =
                tripService.createTrip(
                        userId,
                        new TripCreationRequest(
                                "Draft", TripVisibility.PUBLIC, TripModality.SIMPLE, true, 900));

        for (UpdateType type : UpdateType.values()) {
            assertThatThrownBy(
                            () ->
                                    tripUpdateService.createTripUpdate(
                                            userId,
                                            draftId,
                                            new TripUpdateCreationRequest(HERE, 50, null, type)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CREATED");
        }
        assertThat(tripUpdateRepository.countByTripId(draftId)).isZero();
    }

    @Test
    void migrateDrafts_dryRun_reportsCountsAndChangesNothing() {
        TripPlan plan =
                tripPlanRepository.save(
                        TripPlan.builder()
                                .id(UUID.randomUUID())
                                .name("Existing plan")
                                .planType(TripPlanType.SIMPLE)
                                .userId(userId)
                                .createdTimestamp(Instant.now())
                                .startDate(LocalDate.now())
                                .endDate(LocalDate.now())
                                .startLocation(HERE)
                                .endLocation(HERE)
                                .build());
        Trip withCheckIns = draft("Tuvi Trip Plan", HERE, null);
        tripUpdateRepository.save(
                TripUpdate.builder()
                        .id(UUID.randomUUID())
                        .trip(withCheckIns)
                        .location(HERE)
                        .updateType(UpdateType.REGULAR)
                        .timestamp(Instant.now())
                        .build());
        draft("With route", HERE, null);
        draft("No route", null, null);
        draft("From plan", HERE, plan.getId());

        DraftMigrationReportDTO report = draftTripMigrationService.migrateDrafts(true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.totalDrafts()).isEqualTo(4);
        assertThat(report.toConvert()).isEqualTo(2); // "With route" and "No route"
        assertThat(report.alreadyPlanned()).isEqualTo(1);
        assertThat(report.converted()).isZero();
        assertThat(report.withCheckIns())
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.name()).isEqualTo("Tuvi Trip Plan");
                            assertThat(d.checkIns()).isEqualTo(1);
                        });
        assertThat(report.missingRoute()).extracting(d -> d.name()).containsExactly("No route");
        assertThat(tripRepository.findAllByUserId(userId)).hasSize(4);
        assertThat(tripPlanRepository.count()).isEqualTo(1);
    }

    @Test
    void migrateDrafts_forReal_convertsOnlySafeDrafts() {
        Trip withCheckIns = draft("Has check-ins", HERE, null);
        tripUpdateRepository.save(
                TripUpdate.builder()
                        .id(UUID.randomUUID())
                        .trip(withCheckIns)
                        .updateType(UpdateType.REGULAR)
                        .timestamp(Instant.now())
                        .build());
        Trip convertible = draft("With route", HERE, null);

        DraftMigrationReportDTO report = draftTripMigrationService.migrateDrafts(false);

        assertThat(report.converted()).isEqualTo(1);
        assertThat(report.missingRoute()).isEmpty();
        assertThat(tripRepository.findById(convertible.getId())).isEmpty();
        assertThat(tripRepository.findById(withCheckIns.getId())).isPresent();
        TripPlan plan = tripPlanRepository.findAll().get(0);
        assertThat(plan.getName()).isEqualTo("With route");
        assertThat(plan.getPlanType()).isEqualTo(TripPlanType.SIMPLE);
        assertThat(plan.getMetadata())
                .containsEntry("visibility", "PUBLIC")
                .containsEntry("migratedFromTripId", convertible.getId().toString());
    }

    @Test
    void migrateDrafts_forReal_convertsDraftWithoutRoute() {
        Trip noRoute = draft("No route", null, null);

        DraftMigrationReportDTO report = draftTripMigrationService.migrateDrafts(false);

        assertThat(report.converted()).isEqualTo(1);
        assertThat(report.missingRoute()).extracting(d -> d.name()).containsExactly("No route");
        assertThat(tripRepository.findById(noRoute.getId())).isEmpty();
        TripPlan plan = tripPlanRepository.findAll().get(0);
        assertThat(plan.getName()).isEqualTo("No route");
        assertThat(plan.getStartLocation()).isNull();
        assertThat(plan.getEndLocation()).isNull();
        assertThat(plan.getStartDate()).isEqualTo(plan.getEndDate());
    }

    @Test
    void createTripPlan_withoutLocations_isSaved() {
        UUID planId =
                tripPlanService.createTripPlan(
                        userId,
                        new TripPlanCreationRequest(
                                "Someday walk",
                                LocalDate.of(2026, 11, 1),
                                LocalDate.of(2026, 11, 1),
                                null,
                                null,
                                null,
                                TripPlanType.SIMPLE,
                                null));

        TripPlan plan = tripPlanRepository.findById(planId).orElseThrow();
        assertThat(plan.getStartLocation()).isNull();
        assertThat(plan.getEndLocation()).isNull();
        assertThat(plan.getWaypoints()).isEmpty();
    }

    @Test
    void startTrip_fromPlanWithoutLocations_startsAtCurrentLocation() {
        TripPlan plan =
                tripPlanRepository.save(
                        TripPlan.builder()
                                .id(UUID.randomUUID())
                                .name("No route plan")
                                .planType(TripPlanType.SIMPLE)
                                .userId(userId)
                                .createdTimestamp(Instant.now())
                                .startDate(LocalDate.of(2026, 10, 1))
                                .endDate(LocalDate.of(2026, 10, 1))
                                .build());

        StartTripResponse response =
                tripService.startTrip(
                        userId,
                        "key-1",
                        new StartTripRequest(
                                null,
                                TripVisibility.PUBLIC,
                                null,
                                false,
                                null,
                                HERE,
                                null,
                                null,
                                plan.getId()));

        Trip trip = tripRepository.findById(response.tripId()).orElseThrow();
        assertThat(trip.getName()).isEqualTo("No route plan");
        assertThat(trip.getTripSettings().getTripStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(trip.getTripDetails().getStartLocation().getLat()).isEqualTo(HERE.getLat());
        assertThat(trip.getTripDetails().getEndLocation()).isNull();
        assertThat(tripUpdateRepository.countByTripId(trip.getId())).isEqualTo(1);
    }

    @Test
    void trackPoints_areIdempotentAndDriveRouteAndCheckInDistance() {
        UUID tripId = tripService.startTrip(userId, "key-1", scratch("Walk")).tripId();
        Instant t0 = Instant.now().minusSeconds(600);
        TrackPointsRequest batch =
                new TrackPointsRequest(
                        List.of(
                                new TrackPointsRequest.Point(
                                        UUID.randomUUID(), 52.00, 5.12, null, null, t0),
                                new TrackPointsRequest.Point(
                                        UUID.randomUUID(),
                                        52.01,
                                        5.12,
                                        6.0,
                                        2.0,
                                        t0.plusSeconds(60))));

        assertThat(trackPointService.recordTrackPoints(userId, tripId, batch)).isEqualTo(2);
        assertThat(trackPointService.recordTrackPoints(userId, tripId, batch)).isZero();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> {
                            Trip trip = tripRepository.findById(tripId).orElseThrow();
                            assertThat(trip.getEncodedPolyline()).isNotBlank();
                            assertThat(trip.getCachedDistanceKm()).isBetween(1.10, 1.12);
                        });

        UUID checkIn =
                tripUpdateService.createTripUpdate(
                        userId,
                        tripId,
                        new TripUpdateCreationRequest(
                                null, null, null, UpdateType.DAY_START, null, t0.plusSeconds(120)));
        assertThat(tripUpdateRepository.findById(checkIn).orElseThrow().getDistanceSoFarKm())
                .isBetween(1.10, 1.12);
    }

    private Trip draft(String name, GeoLocation route, UUID planId) {
        return tripRepository.save(
                Trip.builder()
                        .id(UUID.randomUUID())
                        .name(name)
                        .userId(userId)
                        .tripPlanId(planId)
                        .tripSettings(
                                TripSettings.builder()
                                        .tripStatus(TripStatus.CREATED)
                                        .visibility(TripVisibility.PUBLIC)
                                        .tripModality(TripModality.SIMPLE)
                                        .build())
                        .tripDetails(
                                TripDetails.builder()
                                        .startLocation(route)
                                        .endLocation(route)
                                        .build())
                        .creationTimestamp(Instant.now())
                        .enabled(true)
                        .build());
    }
}
