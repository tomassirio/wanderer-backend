package com.tomassirio.wanderer.command.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tomassirio.wanderer.command.controller.request.StartTripRequest;
import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripFromPlanRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateRequest;
import com.tomassirio.wanderer.command.service.TrackPointService;
import com.tomassirio.wanderer.command.service.TripService;
import com.tomassirio.wanderer.command.service.TripUpdateService;
import com.tomassirio.wanderer.command.utils.TestEntityFactory;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.dto.StartTripResponse;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Mock private TripUpdateService tripUpdateService;

    @Mock private TrackPointService trackPointService;

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
        TripCreationRequest request =
                new TripCreationRequest("AB", TripVisibility.PUBLIC, null, null, null);

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
        TripCreationRequest request =
                new TripCreationRequest("", TripVisibility.PUBLIC, null, null, null);

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
        TripCreationRequest request =
                new TripCreationRequest("Summer Road Trip", null, null, null, null);

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

    private static final String START_URL = TRIPS_BASE_URL + "/start";
    private static final String START_BODY =
            "{\"name\":\"Camino\",\"visibility\":\"PUBLIC\",\"automaticUpdates\":true,"
                    + "\"location\":{\"lat\":52.09,\"lon\":5.12}}";

    @Test
    void startTrip_whenNew_shouldReturn201WithIds() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID updateId = UUID.randomUUID();
        when(tripService.startTrip(any(UUID.class), eq("k1"), any(StartTripRequest.class)))
                .thenReturn(new StartTripResponse(tripId, updateId, TripStatus.IN_PROGRESS, false));

        mockMvc.perform(
                        post(START_URL)
                                .header("Idempotency-Key", "k1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(START_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.tripUpdateId").value(updateId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.replayed").value(false));
    }

    @Test
    void startTrip_whenReplayed_shouldReturn200() throws Exception {
        when(tripService.startTrip(any(UUID.class), eq("k1"), any(StartTripRequest.class)))
                .thenReturn(
                        new StartTripResponse(
                                UUID.randomUUID(), UUID.randomUUID(), TripStatus.PAUSED, true));

        mockMvc.perform(
                        post(START_URL)
                                .header("Idempotency-Key", "k1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(START_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    void startTrip_whenConcurrentDuplicate_shouldReplayInsteadOf500() throws Exception {
        StartTripResponse original =
                new StartTripResponse(
                        UUID.randomUUID(), UUID.randomUUID(), TripStatus.IN_PROGRESS, true);
        when(tripService.startTrip(any(UUID.class), eq("k1"), any(StartTripRequest.class)))
                .thenThrow(new DataIntegrityViolationException("uq_trips_user_start"))
                .thenReturn(original);

        mockMvc.perform(
                        post(START_URL)
                                .header("Idempotency-Key", "k1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(START_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(original.tripId().toString()));
    }

    @Test
    void startTrip_withoutIdempotencyKey_shouldReturn400() throws Exception {
        mockMvc.perform(post(START_URL).contentType(MediaType.APPLICATION_JSON).content(START_BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tripService);
    }

    @Test
    void startTrip_withoutNameOrPlan_shouldReturn400() throws Exception {
        mockMvc.perform(
                        post(START_URL)
                                .header("Idempotency-Key", "k1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(START_BODY.replace("\"name\":\"Camino\",", "")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tripService);
    }

    @Test
    void startTrip_whenUserHasOngoingTrip_shouldReturn409() throws Exception {
        when(tripService.startTrip(any(UUID.class), eq("k1"), any(StartTripRequest.class)))
                .thenThrow(new IllegalStateException("User already has a trip in progress."));

        mockMvc.perform(
                        post(START_URL)
                                .header("Idempotency-Key", "k1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(START_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void createTrip_isDeprecated_shouldSendDeprecationHeader() throws Exception {
        doReturn(UUID.randomUUID())
                .when(tripService)
                .createTrip(any(UUID.class), any(TripCreationRequest.class));

        mockMvc.perform(
                        post(TRIPS_BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                TestEntityFactory.createTripCreationRequest(
                                                        "Old client", TripVisibility.PUBLIC))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Deprecation", "true"));
    }

    // --- Check-ins ---

    private static final String TRIP_UPDATES_URL = TRIPS_BASE_URL + "/{tripId}/updates";
    private static final String TRACK_POINTS_URL = TRIPS_BASE_URL + "/{tripId}/track-points";

    @Test
    void createTripUpdate_withClientIdAndRecordedAt_passesThemThrough() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(tripUpdateService.createTripUpdate(any(), eq(tripId), any())).thenReturn(clientId);

        mockMvc.perform(
                        post(TRIP_UPDATES_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"location":{"lat":52.09,"lon":5.12},"updateType":"REGULAR",
                                         "id":"%s","recordedAt":"2026-10-08T09:15:02Z"}
                                        """
                                                .formatted(clientId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(clientId.toString()));

        ArgumentCaptor<TripUpdateCreationRequest> captor =
                ArgumentCaptor.forClass(TripUpdateCreationRequest.class);
        verify(tripUpdateService).createTripUpdate(any(), eq(tripId), captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(clientId);
        assertThat(captor.getValue().recordedAt()).isEqualTo(Instant.parse("2026-10-08T09:15:02Z"));
    }

    @Test
    void createTripUpdate_lifecycleMarkerWithoutLocation_isAccepted() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(tripUpdateService.createTripUpdate(any(), eq(tripId), any()))
                .thenReturn(UUID.randomUUID());

        mockMvc.perform(
                        post(TRIP_UPDATES_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"updateType\":\"DAY_END\"}"))
                .andExpect(status().isAccepted());
    }

    // --- Track points ---

    private static String trackPoints(int count) {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                points.append(',');
            }
            points.append(
                    """
                    {"id":"%s","lat":52.09,"lon":5.12,"accuracyM":8.0,"altitudeM":3.1,
                     "recordedAt":"2026-10-08T09:15:02Z"}"""
                            .formatted(UUID.randomUUID()));
        }
        return "{\"points\":[" + points + "]}";
    }

    @Test
    void uploadTrackPoints_returnsAcceptedCount() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(trackPointService.recordTrackPoints(any(), eq(tripId), any())).thenReturn(2);

        mockMvc.perform(
                        post(TRACK_POINTS_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(trackPoints(3)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2));
    }

    @Test
    void uploadTrackPoints_withoutPoints_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post(TRACK_POINTS_URL, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"points\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(trackPointService);
    }

    @Test
    void uploadTrackPoints_withMoreThan500Points_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post(TRACK_POINTS_URL, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(trackPoints(501)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(trackPointService);
    }

    @Test
    void uploadTrackPoints_withPointMissingId_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post(TRACK_POINTS_URL, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"points\":[{\"lat\":1.0,\"lon\":2.0,"
                                                + "\"recordedAt\":\"2026-10-08T09:15:02Z\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(trackPointService);
    }

    @Test
    void uploadTrackPoints_whenTripNotLive_returnsConflict() throws Exception {
        UUID tripId = UUID.randomUUID();
        when(trackPointService.recordTrackPoints(any(), eq(tripId), any()))
                .thenThrow(new IllegalStateException("Track points are not accepted"));

        mockMvc.perform(
                        post(TRACK_POINTS_URL, tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(trackPoints(1)))
                .andExpect(status().isConflict());
    }
}
