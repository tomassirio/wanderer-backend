package com.tomassirio.wanderer.command.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.command.service.PolylineService;
import com.tomassirio.wanderer.command.service.PromotedTripService;
import com.tomassirio.wanderer.command.service.ThumbnailBackfillService;
import com.tomassirio.wanderer.command.service.TripUpdateGeocodingService;
import com.tomassirio.wanderer.commons.dto.ThumbnailBackfillResultDTO;
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
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class AdminTripControllerTest {

    private static final String ADMIN_TRIPS_URL = "/api/1/admin/trips";

    private MockMvc mockMvc;

    @Mock private PolylineService polylineService;

    @Mock private PromotedTripService promotedTripService;

    @Mock private TripUpdateGeocodingService tripUpdateGeocodingService;

    @Mock private ThumbnailBackfillService thumbnailBackfillService;

    @InjectMocks private AdminTripController adminTripController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        adminTripController, new GlobalExceptionHandler());
    }

    // ================================================================
    // Regenerate missing thumbnails
    // ================================================================

    @Test
    void regenerateMissingThumbnails_shouldReturnSummary() throws Exception {
        when(thumbnailBackfillService.regenerateMissingTripThumbnails())
                .thenReturn(new ThumbnailBackfillResultDTO(10, 4, 3, 1));

        mockMvc.perform(post(ADMIN_TRIPS_URL + "/thumbnails/regenerate-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(10))
                .andExpect(jsonPath("$.missing").value(4))
                .andExpect(jsonPath("$.regenerated").value(3))
                .andExpect(jsonPath("$.failed").value(1));
    }

    // ================================================================
    // Recompute polyline
    // ================================================================

    @Test
    void recomputePolyline_shouldReturnNoContent() throws Exception {
        UUID tripId = UUID.randomUUID();
        doNothing().when(polylineService).recomputePolyline(tripId);

        mockMvc.perform(post(ADMIN_TRIPS_URL + "/{tripId}/recompute-polyline", tripId))
                .andExpect(status().isNoContent());

        verify(polylineService).recomputePolyline(tripId);
    }

    @Test
    void recomputePolyline_whenTripNotFound_shouldReturnNotFound() throws Exception {
        UUID tripId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Trip not found: " + tripId))
                .when(polylineService)
                .recomputePolyline(tripId);

        mockMvc.perform(post(ADMIN_TRIPS_URL + "/{tripId}/recompute-polyline", tripId))
                .andExpect(status().isNotFound());
    }

    @Test
    void recomputePolyline_whenServiceFails_shouldReturnInternalServerError() throws Exception {
        UUID tripId = UUID.randomUUID();
        doThrow(new RuntimeException("Google API unavailable"))
                .when(polylineService)
                .recomputePolyline(tripId);

        mockMvc.perform(post(ADMIN_TRIPS_URL + "/{tripId}/recompute-polyline", tripId))
                .andExpect(status().isInternalServerError());
    }

    // ================================================================
    // Recompute geocoding
    // ================================================================

    @Test
    void recomputeGeocoding_shouldReturnNoContent() throws Exception {
        UUID tripId = UUID.randomUUID();
        doNothing().when(tripUpdateGeocodingService).recomputeGeocoding(tripId);

        mockMvc.perform(post(ADMIN_TRIPS_URL + "/{tripId}/recompute-geocoding", tripId))
                .andExpect(status().isNoContent());

        verify(tripUpdateGeocodingService).recomputeGeocoding(tripId);
    }

    @Test
    void recomputeGeocoding_whenTripNotFound_shouldReturnNotFound() throws Exception {
        UUID tripId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Trip not found: " + tripId))
                .when(tripUpdateGeocodingService)
                .recomputeGeocoding(tripId);

        mockMvc.perform(post(ADMIN_TRIPS_URL + "/{tripId}/recompute-geocoding", tripId))
                .andExpect(status().isNotFound());
    }

    @Test
    void recomputeGeocoding_whenServiceFails_shouldReturnInternalServerError() throws Exception {
        UUID tripId = UUID.randomUUID();
        doThrow(new RuntimeException("Geocoding API unavailable"))
                .when(tripUpdateGeocodingService)
                .recomputeGeocoding(tripId);

        mockMvc.perform(post(ADMIN_TRIPS_URL + "/{tripId}/recompute-geocoding", tripId))
                .andExpect(status().isInternalServerError());
    }

    // ================================================================
    // Promote trip
    // ================================================================

    @Test
    void promoteTrip_whenAdminPromotesWithDonationLink_shouldReturnAccepted() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID promotedTripId = UUID.randomUUID();
        String donationLink = "https://example.com/donate";

        String requestBody = String.format("{\"donationLink\":\"%s\"}", donationLink);

        when(promotedTripService.promoteTrip(
                        any(UUID.class), eq(tripId), eq(donationLink), eq(false), eq(null)))
                .thenReturn(promotedTripId);

        mockMvc.perform(
                        post(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(promotedTripId.toString()));
    }

    @Test
    void promoteTrip_whenAdminPromotesWithoutDonationLink_shouldReturnAccepted() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID promotedTripId = UUID.randomUUID();

        when(promotedTripService.promoteTrip(
                        any(UUID.class), eq(tripId), eq(null), eq(false), eq(null)))
                .thenReturn(promotedTripId);

        mockMvc.perform(
                        post(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(promotedTripId.toString()));
    }

    @Test
    void promoteTrip_whenTripNotFound_shouldReturnNotFound() throws Exception {
        UUID tripId = UUID.randomUUID();
        String requestBody = "{\"donationLink\":\"https://example.com/donate\"}";

        when(promotedTripService.promoteTrip(
                        any(UUID.class), eq(tripId), any(), any(Boolean.class), any()))
                .thenThrow(new EntityNotFoundException("Trip not found"));

        mockMvc.perform(
                        post(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void promoteTrip_whenTripAlreadyPromoted_shouldReturnConflict() throws Exception {
        UUID tripId = UUID.randomUUID();
        String requestBody = "{\"donationLink\":\"https://example.com/donate\"}";

        when(promotedTripService.promoteTrip(
                        any(UUID.class), eq(tripId), any(), any(Boolean.class), any()))
                .thenThrow(new IllegalStateException("Trip is already promoted"));

        mockMvc.perform(
                        post(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isConflict());
    }

    // ================================================================
    // Unpromote trip
    // ================================================================

    @Test
    void unpromoteTrip_shouldReturnAccepted() throws Exception {
        UUID tripId = UUID.randomUUID();
        doNothing().when(promotedTripService).unpromoteTrip(any(UUID.class), eq(tripId));

        mockMvc.perform(delete(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId))
                .andExpect(status().isAccepted());

        verify(promotedTripService).unpromoteTrip(any(UUID.class), eq(tripId));
    }

    @Test
    void unpromoteTrip_whenTripNotFound_shouldReturnNotFound() throws Exception {
        UUID tripId = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Trip not found"))
                .when(promotedTripService)
                .unpromoteTrip(any(UUID.class), eq(tripId));

        mockMvc.perform(delete(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Update donation link
    // ================================================================

    @Test
    void updateDonationLink_shouldReturnAccepted() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID promotedTripId = UUID.randomUUID();
        String requestBody = "{\"donationLink\":\"https://example.com/new-donate\"}";

        when(promotedTripService.updatePromotedTripDonationLink(
                        any(UUID.class), eq(tripId), eq("https://example.com/new-donate")))
                .thenReturn(promotedTripId);

        mockMvc.perform(
                        put(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value(promotedTripId.toString()));
    }

    @Test
    void updateDonationLink_whenTripNotFound_shouldReturnNotFound() throws Exception {
        UUID tripId = UUID.randomUUID();
        String requestBody = "{\"donationLink\":\"https://example.com/donate\"}";

        when(promotedTripService.updatePromotedTripDonationLink(any(UUID.class), eq(tripId), any()))
                .thenThrow(new EntityNotFoundException("Promoted trip not found"));

        mockMvc.perform(
                        put(ADMIN_TRIPS_URL + "/{tripId}/promote", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isNotFound());
    }
}
