package com.tomassirio.wanderer.query.controller;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.dto.TripPlanDTO;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import com.tomassirio.wanderer.query.service.TripPlanService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class TripPlanControllerTest {

    private static final String TRIP_PLANS_BASE_URL = "/api/1/trips/plans";
    private static final String TRIP_PLANS_ME_URL = TRIP_PLANS_BASE_URL + "/me";

    private MockMvc mockMvc;

    @Mock private TripPlanService tripPlanService;

    @InjectMocks private TripPlanController tripPlanController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        tripPlanController, new GlobalExceptionHandler());
    }

    @Test
    void getTripPlan_whenTripPlanExists_shouldReturnTripPlan() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        TripPlanDTO tripPlan =
                createTripPlanDTO(planId, "Summer Vacation Plan", TripPlanType.MULTI_DAY);

        when(tripPlanService.getTripPlan(planId)).thenReturn(tripPlan);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.name").value("Summer Vacation Plan"))
                .andExpect(jsonPath("$.planType").value("MULTI_DAY"))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void getTripPlan_whenTripPlanDoesNotExist_shouldReturnNotFound() throws Exception {
        // Given
        UUID nonExistentPlanId = UUID.randomUUID();

        when(tripPlanService.getTripPlan(nonExistentPlanId))
                .thenThrow(new EntityNotFoundException("Trip plan not found"));

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", nonExistentPlanId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTripPlan_whenTripPlanHasWaypoints_shouldReturnTripPlanWithWaypoints() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        GeoLocation waypoint1 = new GeoLocation(40.7128, -74.0060); // New York
        GeoLocation waypoint2 = new GeoLocation(41.8781, -87.6298); // Chicago
        GeoLocation waypoint3 = new GeoLocation(34.0522, -118.2437); // Los Angeles

        TripPlanDTO tripPlan =
                new TripPlanDTO(
                        planId.toString(),
                        USER_ID.toString(),
                        "Cross Country Trip",
                        TripPlanType.MULTI_DAY,
                        LocalDate.now(),
                        LocalDate.now().plusDays(10),
                        waypoint1,
                        waypoint3,
                        List.of(waypoint1, waypoint2, waypoint3),
                        null,
                        null,
                        null,
                        Instant.now());

        when(tripPlanService.getTripPlan(planId)).thenReturn(tripPlan);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.name").value("Cross Country Trip"))
                .andExpect(jsonPath("$.waypoints").isArray())
                .andExpect(jsonPath("$.waypoints.length()").value(3))
                .andExpect(jsonPath("$.waypoints[0].lat").value(40.7128))
                .andExpect(jsonPath("$.waypoints[0].lon").value(-74.0060))
                .andExpect(jsonPath("$.waypoints[1].lat").value(41.8781))
                .andExpect(jsonPath("$.waypoints[1].lon").value(-87.6298))
                .andExpect(jsonPath("$.waypoints[2].lat").value(34.0522))
                .andExpect(jsonPath("$.waypoints[2].lon").value(-118.2437));
    }

    @Test
    void getTripPlan_whenTripPlanIsOneDayTrip_shouldReturnOneDayTripPlan() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        TripPlanDTO tripPlan = createTripPlanDTO(planId, "Day Trip", TripPlanType.SIMPLE);

        when(tripPlanService.getTripPlan(planId)).thenReturn(tripPlan);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.name").value("Day Trip"))
                .andExpect(jsonPath("$.planType").value("SIMPLE"));
    }

    @Test
    void getMyTripPlans_whenTripPlansExist_shouldReturnListOfTripPlans() throws Exception {
        // Given
        List<TripPlanDTO> tripPlans =
                List.of(
                        createTripPlanDTO(UUID.randomUUID(), "Plan 1", TripPlanType.MULTI_DAY),
                        createTripPlanDTO(UUID.randomUUID(), "Plan 2", TripPlanType.SIMPLE));

        when(tripPlanService.getTripPlansForUser(USER_ID)).thenReturn(tripPlans);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Plan 1"))
                .andExpect(jsonPath("$[0].planType").value("MULTI_DAY"))
                .andExpect(jsonPath("$[1].name").value("Plan 2"))
                .andExpect(jsonPath("$[1].planType").value("SIMPLE"));
    }

    @Test
    void getMyTripPlans_whenNoTripPlansExist_shouldReturnEmptyList() throws Exception {
        // Given
        when(tripPlanService.getTripPlansForUser(USER_ID)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMyTripPlans_withMultipleTripPlans_shouldReturnAllMyTripPlans() throws Exception {
        // Given
        List<TripPlanDTO> tripPlans =
                List.of(
                        createTripPlanDTO(UUID.randomUUID(), "Plan A", TripPlanType.MULTI_DAY),
                        createTripPlanDTO(UUID.randomUUID(), "Plan B", TripPlanType.SIMPLE),
                        createTripPlanDTO(UUID.randomUUID(), "Plan C", TripPlanType.MULTI_DAY),
                        createTripPlanDTO(UUID.randomUUID(), "Plan D", TripPlanType.SIMPLE));

        when(tripPlanService.getTripPlansForUser(USER_ID)).thenReturn(tripPlans);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("Plan A"))
                .andExpect(jsonPath("$[1].name").value("Plan B"))
                .andExpect(jsonPath("$[2].name").value("Plan C"))
                .andExpect(jsonPath("$[3].name").value("Plan D"))
                .andExpect(jsonPath("$[3].planType").value("SIMPLE"));
    }

    @Test
    void getMyTripPlans_whenUserHasNoPlans_shouldReturnEmptyList() throws Exception {
        // Given
        when(tripPlanService.getTripPlansForUser(USER_ID)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getTripPlan_withStartAndEndDates_shouldReturnTripPlanWithDates() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2025, 6, 15);
        LocalDate endDate = LocalDate.of(2025, 6, 25);

        TripPlanDTO tripPlan =
                new TripPlanDTO(
                        planId.toString(),
                        USER_ID.toString(),
                        "Summer Road Trip",
                        TripPlanType.MULTI_DAY,
                        startDate,
                        endDate,
                        new GeoLocation(40.7128, -74.0060),
                        new GeoLocation(34.0522, -118.2437),
                        List.of(),
                        null,
                        null,
                        null,
                        Instant.now());

        when(tripPlanService.getTripPlan(planId)).thenReturn(tripPlan);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate[0]").value(2025))
                .andExpect(jsonPath("$.startDate[1]").value(6))
                .andExpect(jsonPath("$.startDate[2]").value(15))
                .andExpect(jsonPath("$.endDate[0]").value(2025))
                .andExpect(jsonPath("$.endDate[1]").value(6))
                .andExpect(jsonPath("$.endDate[2]").value(25));
    }

    @Test
    void getTripPlan_withStartAndEndLocations_shouldReturnTripPlanWithLocations() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        GeoLocation startLocation = new GeoLocation(51.5074, -0.1278); // London
        GeoLocation endLocation = new GeoLocation(48.8566, 2.3522); // Paris

        TripPlanDTO tripPlan =
                new TripPlanDTO(
                        planId.toString(),
                        USER_ID.toString(),
                        "Europe Trip",
                        TripPlanType.MULTI_DAY,
                        LocalDate.now(),
                        LocalDate.now().plusDays(5),
                        startLocation,
                        endLocation,
                        List.of(),
                        null,
                        null,
                        null,
                        Instant.now());

        when(tripPlanService.getTripPlan(planId)).thenReturn(tripPlan);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startLocation.lat").value(51.5074))
                .andExpect(jsonPath("$.startLocation.lon").value(-0.1278))
                .andExpect(jsonPath("$.endLocation.lat").value(48.8566))
                .andExpect(jsonPath("$.endLocation.lon").value(2.3522));
    }

    @Test
    void getTripPlan_whenRoundTripType_shouldReturnRoundTripPlan() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        TripPlanDTO tripPlan = createTripPlanDTO(planId, "Simple Trip", TripPlanType.SIMPLE);

        when(tripPlanService.getTripPlan(planId)).thenReturn(tripPlan);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("SIMPLE"));
    }

    @Test
    void getTripPlan_withPolylineData_shouldReturnPolylineInResponse() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        Instant polylineUpdatedAt = Instant.parse("2026-03-01T14:30:00Z");

        TripPlanDTO tripPlan =
                new TripPlanDTO(
                        planId.toString(),
                        USER_ID.toString(),
                        "Polyline Plan",
                        TripPlanType.MULTI_DAY,
                        LocalDate.now(),
                        LocalDate.now().plusDays(7),
                        new GeoLocation(42.88, -8.54),
                        new GeoLocation(43.01, -8.55),
                        List.of(),
                        "a~l~Fjk~uOwHJy@P",
                        null,
                        polylineUpdatedAt,
                        Instant.now());

        when(tripPlanService.getTripPlan(planId)).thenReturn(tripPlan);

        // When & Then
        mockMvc.perform(get(TRIP_PLANS_BASE_URL + "/{planId}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encodedPolyline").value("a~l~Fjk~uOwHJy@P"))
                .andExpect(jsonPath("$.polylineUpdatedAt").exists());
    }

    private TripPlanDTO createTripPlanDTO(UUID planId, String name, TripPlanType planType) {
        return new TripPlanDTO(
                planId.toString(),
                USER_ID.toString(),
                name,
                planType,
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                new GeoLocation(0.0, 0.0),
                new GeoLocation(0.0, 0.0),
                List.of(),
                null,
                null,
                null,
                Instant.now());
    }
}
