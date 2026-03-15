package com.tomassirio.wanderer.command.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripFromPlanRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateRequest;
import com.tomassirio.wanderer.command.service.TripService;
import com.tomassirio.wanderer.command.utils.TestEntityFactory;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import jakarta.persistence.EntityNotFoundException;
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
class TripControllerTest {

    private static final String TRIPS_BASE_URL = "/api/1/trips";
    private static final String TRIP_BY_ID_URL = TRIPS_BASE_URL + "/{id}";
    private static final String TRIP_FROM_PLAN_URL = TRIPS_BASE_URL + "/from-plan/{tripPlanId}";

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock private TripService tripService;

    @InjectMocks private TripController tripController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Use shared test utility from commons to register @CurrentUserId resolver
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        tripController, new GlobalExceptionHandler());
    }

    @Test
    void createTrip_whenValidRequest_shouldReturnCreatedTrip() throws Exception {
        // Given
        TripCreationRequest request =
                TestEntityFactory.createTripCreationRequest(
                        "Summer Road Trip 2025", TripVisibility.PUBLIC);

        UUID tripId = UUID.randomUUID();

        doReturn(tripId)
                .when(tripService)
                .createTrip(any(UUID.class), any(TripCreationRequest.class));

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void createTrip_whenPrivateVisibility_shouldReturnCreatedTrip() throws Exception {
        // Given
        TripCreationRequest request =
                TestEntityFactory.createTripCreationRequest(
                        "Private Road Trip", TripVisibility.PRIVATE);

        UUID tripId = UUID.randomUUID();

        doReturn(tripId)
                .when(tripService)
                .createTrip(any(UUID.class), any(TripCreationRequest.class));

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void createTrip_whenNameIsTooShort_shouldReturnBadRequest() throws Exception {
        // Given
        TripCreationRequest request = new TripCreationRequest("AB", TripVisibility.PUBLIC, null, null, null);

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTrip_whenNameIsBlank_shouldReturnBadRequest() throws Exception {
        // Given
        TripCreationRequest request = new TripCreationRequest("", TripVisibility.PUBLIC, null, null, null);

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTrip_whenNameIsTooLong_shouldReturnBadRequest() throws Exception {
        // Given - name with more than 100 characters
        String longName = "A".repeat(101);
        TripCreationRequest request =
                new TripCreationRequest(longName, TripVisibility.PUBLIC, null, null, null);

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTrip_whenVisibilityIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        TripCreationRequest request = new TripCreationRequest("Summer Road Trip", null, null, null, null);

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTrip_whenUpdateRefreshBelowMinimum_shouldReturnBadRequest() throws Exception {
        // Given - updateRefresh is 10, below the minimum of 15
        TripCreationRequest request =
                new TripCreationRequest("Summer Road Trip", TripVisibility.PUBLIC, null, null, 10);

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTrip_whenProtectedVisibility_shouldReturnCreatedTrip() throws Exception {
        // Given
        TripCreationRequest request =
                TestEntityFactory.createTripCreationRequest(
                        "Protected Trip", TripVisibility.PROTECTED);

        UUID tripId = UUID.randomUUID();

        doReturn(tripId)
                .when(tripService)
                .createTrip(any(UUID.class), any(TripCreationRequest.class));

        // When & Then
        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void updateTrip_whenValidRequest_shouldReturnUpdatedTrip() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdateRequest request =
                TestEntityFactory.createTripUpdateRequest(
                        "Updated Trip Name", TripVisibility.PUBLIC);

        when(tripService.updateTrip(any(UUID.class), eq(tripId), any(TripUpdateRequest.class)))
                .thenReturn(tripId);

        // When & Then
        mockMvc.perform(
                        put(TRIP_BY_ID_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void updateTrip_whenChangingVisibility_shouldReturnUpdatedTrip() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdateRequest request =
                TestEntityFactory.createTripUpdateRequest("Trip Name", TripVisibility.PRIVATE);

        when(tripService.updateTrip(any(UUID.class), eq(tripId), any(TripUpdateRequest.class)))
                .thenReturn(tripId);

        // When & Then
        mockMvc.perform(
                        put(TRIP_BY_ID_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void updateTrip_whenTripNotFound_shouldReturnNotFound() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdateRequest request =
                TestEntityFactory.createTripUpdateRequest("Updated Trip", TripVisibility.PUBLIC);

        when(tripService.updateTrip(any(UUID.class), eq(tripId), any(TripUpdateRequest.class)))
                .thenThrow(new EntityNotFoundException("Trip not found"));

        // When & Then
        mockMvc.perform(
                        put(TRIP_BY_ID_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTrip_whenNameIsBlank_shouldReturnBadRequest() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdateRequest request = new TripUpdateRequest("", TripVisibility.PUBLIC);

        // When & Then
        mockMvc.perform(
                        put(TRIP_BY_ID_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTrip_whenNameIsTooShort_shouldReturnBadRequest() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdateRequest request = new TripUpdateRequest("AB", TripVisibility.PUBLIC);

        // When & Then
        mockMvc.perform(
                        put(TRIP_BY_ID_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTrip_whenVisibilityIsNull_shouldReturnBadRequest() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripUpdateRequest request = new TripUpdateRequest("Valid Trip Name", null);

        // When & Then
        mockMvc.perform(
                        put(TRIP_BY_ID_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTrip_whenTripExists_shouldReturnNoContent() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        doNothing().when(tripService).deleteTrip(any(UUID.class), eq(tripId));

        // When & Then
        mockMvc.perform(delete(TRIP_BY_ID_URL, tripId)).andExpect(status().isAccepted());
    }

    @Test
    void deleteTrip_whenTripNotFound_shouldReturnNotFound() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Trip not found"))
                .when(tripService)
                .deleteTrip(any(UUID.class), eq(tripId));

        // When & Then
        mockMvc.perform(delete(TRIP_BY_ID_URL, tripId)).andExpect(status().isNotFound());
    }

    // Tests for createTripFromPlan endpoint

    @Test
    void createTripFromPlan_whenValidRequest_shouldReturnCreatedTrip() throws Exception {
        // Given
        UUID tripPlanId = UUID.randomUUID();
        TripFromPlanRequest request =
                new TripFromPlanRequest(TripVisibility.PUBLIC, null, null, null);

        UUID tripId = UUID.randomUUID();

        doReturn(tripId)
                .when(tripService)
                .createTripFromPlan(
                        any(UUID.class), any(UUID.class), any(TripFromPlanRequest.class));

        // When & Then
        mockMvc.perform(
                        post(TRIP_FROM_PLAN_URL, tripPlanId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void createTripFromPlan_whenPrivateVisibility_shouldReturnCreatedTrip() throws Exception {
        // Given
        UUID tripPlanId = UUID.randomUUID();
        TripFromPlanRequest request =
                new TripFromPlanRequest(TripVisibility.PRIVATE, null, null, null);

        UUID tripId = UUID.randomUUID();

        doReturn(tripId)
                .when(tripService)
                .createTripFromPlan(
                        any(UUID.class), any(UUID.class), any(TripFromPlanRequest.class));

        // When & Then
        mockMvc.perform(
                        post(TRIP_FROM_PLAN_URL, tripPlanId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void createTripFromPlan_whenVisibilityIsInvalid_shouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(
                        post(TRIP_FROM_PLAN_URL, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"visibility\": \"INVALID_VISIBILITY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTripFromPlan_whenTripPlanNotFound_shouldReturnNotFound() throws Exception {
        // Given
        UUID nonExistentPlanId = UUID.randomUUID();
        TripFromPlanRequest request =
                new TripFromPlanRequest(TripVisibility.PUBLIC, null, null, null);

        doThrow(new EntityNotFoundException("Trip plan not found"))
                .when(tripService)
                .createTripFromPlan(
                        any(UUID.class), any(UUID.class), any(TripFromPlanRequest.class));

        // When & Then
        mockMvc.perform(
                        post(TRIP_FROM_PLAN_URL, nonExistentPlanId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTripFromPlan_whenUserNotOwner_shouldReturnForbidden() throws Exception {
        // Given
        UUID tripPlanId = UUID.randomUUID();
        TripFromPlanRequest request =
                new TripFromPlanRequest(TripVisibility.PUBLIC, null, null, null);

        doThrow(new AccessDeniedException("User does not have permission to access trip plan"))
                .when(tripService)
                .createTripFromPlan(
                        any(UUID.class), any(UUID.class), any(TripFromPlanRequest.class));

        // When & Then
        mockMvc.perform(
                        post(TRIP_FROM_PLAN_URL, tripPlanId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSettings_whenValidRequest_shouldReturnAccepted() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        String requestBody = "{\"updateRefresh\": 120, \"automaticUpdates\": true}";

        when(tripService.updateSettings(any(UUID.class), eq(tripId), eq(120), eq(true), any()))
                .thenReturn(tripId);

        // When & Then
        mockMvc.perform(
                        patch(TRIPS_BASE_URL + "/{id}/settings", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void updateSettings_whenPartialUpdate_shouldReturnAccepted() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        String requestBody = "{\"automaticUpdates\": false}";

        when(tripService.updateSettings(any(UUID.class), eq(tripId), eq(null), eq(false), any()))
                .thenReturn(tripId);

        // When & Then
        mockMvc.perform(
                        patch(TRIPS_BASE_URL + "/{id}/settings", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(tripId.toString()));
    }

    @Test
    void updateSettings_whenTripNotFound_shouldReturnNotFound() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        String requestBody = "{\"updateRefresh\": 120, \"automaticUpdates\": true}";

        when(tripService.updateSettings(any(UUID.class), eq(tripId), any(), any(), any()))
                .thenThrow(new EntityNotFoundException("Trip not found"));

        // When & Then
        mockMvc.perform(
                        patch(TRIPS_BASE_URL + "/{id}/settings", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSettings_whenUserDoesNotOwnTrip_shouldReturnForbidden() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        String requestBody = "{\"updateRefresh\": 120, \"automaticUpdates\": true}";

        when(tripService.updateSettings(any(UUID.class), eq(tripId), any(), any(), any()))
                .thenThrow(new AccessDeniedException("Access denied"));

        // When & Then
        mockMvc.perform(
                        patch(TRIPS_BASE_URL + "/{id}/settings", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isForbidden());
    }
}
