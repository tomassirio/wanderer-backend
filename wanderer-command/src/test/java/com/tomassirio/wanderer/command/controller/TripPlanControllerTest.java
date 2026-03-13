package com.tomassirio.wanderer.command.controller;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tomassirio.wanderer.command.controller.request.TripPlanCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripPlanUpdateRequest;
import com.tomassirio.wanderer.command.service.TripPlanService;
import com.tomassirio.wanderer.command.utils.TestEntityFactory;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class TripPlanControllerTest {

    private static final String TRIP_PLANS_BASE_URL = "/api/1/trips/plans";
    private static final String TRIP_PLAN_BY_ID_URL = TRIP_PLANS_BASE_URL + "/{planId}";

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock private TripPlanService tripPlanService;

    @InjectMocks private TripPlanController tripPlanController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        tripPlanController, USER_ID, objectMapper, new GlobalExceptionHandler());
    }

    @Test
    void createTripPlan_whenValidRequest_shouldReturnCreatedPlan() throws Exception {
        // Given
        TripPlanCreationRequest request =
                TestEntityFactory.createTripPlanCreationRequest("Europe Summer Trip 2025");

        UUID planId = UUID.randomUUID();

        when(tripPlanService.createTripPlan(any(UUID.class), any(TripPlanCreationRequest.class)))
                .thenReturn(planId);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(planId.toString()));
    }

    @Test
    void createTripPlan_whenMultiDayPlanType_shouldReturnCreatedPlan() throws Exception {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(10);
        GeoLocation startLocation = TestEntityFactory.createGeoLocation(51.5074, -0.1278);
        GeoLocation endLocation = TestEntityFactory.createGeoLocation(48.8566, 2.3522);

        TripPlanCreationRequest request =
                TestEntityFactory.createTripPlanCreationRequest(
                        "Multi-Day Adventure",
                        startDate,
                        endDate,
                        startLocation,
                        endLocation,
                        TripPlanType.MULTI_DAY);

        UUID planId = UUID.randomUUID();

        when(tripPlanService.createTripPlan(any(UUID.class), any(TripPlanCreationRequest.class)))
                .thenReturn(planId);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(planId.toString()));
    }

    @Test
    void createTripPlan_whenNameIsTooShort_shouldReturnBadRequest() throws Exception {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "AB",
                        startDate,
                        endDate,
                        location,
                        location,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripPlan_whenNameIsBlank_shouldReturnBadRequest() throws Exception {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "",
                        startDate,
                        endDate,
                        location,
                        location,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripPlan_whenNameIsTooLong_shouldReturnBadRequest() throws Exception {
        // Given - name with more than 100 characters
        String longName = "A".repeat(101);
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        longName,
                        startDate,
                        endDate,
                        location,
                        location,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripPlan_whenStartDateIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Valid Name",
                        null,
                        endDate,
                        location,
                        location,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripPlan_whenEndDateIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(1);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Valid Name",
                        startDate,
                        null,
                        location,
                        location,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripPlan_whenStartLocationIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation endLocation = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Valid Name",
                        startDate,
                        endDate,
                        null,
                        endLocation,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripPlan_whenEndLocationIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation startLocation = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Valid Name",
                        startDate,
                        endDate,
                        startLocation,
                        null,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripPlan_whenPlanTypeIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Valid Name",
                        startDate,
                        endDate,
                        location,
                        location,
                        List.of(),
                        null,
                        null);

        // When & Then
        mockMvc.perform(
                        post(TRIP_PLANS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTripPlan_whenValidRequest_shouldReturnUpdatedPlan() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        TripPlanUpdateRequest request =
                TestEntityFactory.createTripPlanUpdateRequest("Updated Plan Name");

        when(tripPlanService.updateTripPlan(
                        any(UUID.class), eq(planId), any(TripPlanUpdateRequest.class)))
                .thenReturn(planId);

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(planId.toString()));
    }

    @Test
    void updateTripPlan_whenChangingDates_shouldReturnUpdatedPlan() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        LocalDate newStartDate = LocalDate.now().plusDays(5);
        LocalDate newEndDate = LocalDate.now().plusDays(15);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanUpdateRequest request =
                TestEntityFactory.createTripPlanUpdateRequest(
                        "Plan Name", newStartDate, newEndDate, location, location);

        when(tripPlanService.updateTripPlan(
                        any(UUID.class), eq(planId), any(TripPlanUpdateRequest.class)))
                .thenReturn(planId);

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(planId.toString()));
    }

    @Test
    void updateTripPlan_whenPlanNotFound_shouldReturnNotFound() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        TripPlanUpdateRequest request =
                TestEntityFactory.createTripPlanUpdateRequest("Updated Plan");

        when(tripPlanService.updateTripPlan(
                        any(UUID.class), eq(planId), any(TripPlanUpdateRequest.class)))
                .thenThrow(new EntityNotFoundException("Trip plan not found"));

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTripPlan_whenUserDoesNotOwnPlan_shouldReturnForbidden() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        TripPlanUpdateRequest request =
                TestEntityFactory.createTripPlanUpdateRequest("Updated Plan");

        when(tripPlanService.updateTripPlan(
                        any(UUID.class), eq(planId), any(TripPlanUpdateRequest.class)))
                .thenThrow(new AccessDeniedException("User does not own this plan"));

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTripPlan_whenNameIsBlank_shouldReturnBadRequest() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "", startDate, endDate, location, location, List.of(), null);

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTripPlan_whenNameIsTooShort_shouldReturnBadRequest() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "AB", startDate, endDate, location, location, List.of(), null);

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTripPlan_whenStartDateIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        LocalDate endDate = LocalDate.now().plusDays(7);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "Valid Name", null, endDate, location, location, List.of(), null);

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTripPlan_whenEndDateIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        LocalDate startDate = LocalDate.now().plusDays(1);
        GeoLocation location = TestEntityFactory.createGeoLocation(0.0, 0.0);

        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "Valid Name", startDate, null, location, location, List.of(), null);

        // When & Then
        mockMvc.perform(
                        put(TRIP_PLAN_BY_ID_URL, planId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTripPlan_whenPlanExists_shouldReturnNoContent() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        doNothing().when(tripPlanService).deleteTripPlan(any(UUID.class), eq(planId));

        // When & Then
        mockMvc.perform(delete(TRIP_PLAN_BY_ID_URL, planId)).andExpect(status().isAccepted());
    }

    @Test
    void deleteTripPlan_whenPlanNotFound_shouldReturnNotFound() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Trip plan not found"))
                .when(tripPlanService)
                .deleteTripPlan(any(UUID.class), eq(planId));

        // When & Then
        mockMvc.perform(delete(TRIP_PLAN_BY_ID_URL, planId)).andExpect(status().isNotFound());
    }

    @Test
    void deleteTripPlan_whenUserDoesNotOwnPlan_shouldReturnForbidden() throws Exception {
        // Given
        UUID planId = UUID.randomUUID();
        doThrow(new AccessDeniedException("User does not own this plan"))
                .when(tripPlanService)
                .deleteTripPlan(any(UUID.class), eq(planId));

        // When & Then
        mockMvc.perform(delete(TRIP_PLAN_BY_ID_URL, planId)).andExpect(status().isForbidden());
    }
}
