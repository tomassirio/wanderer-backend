package com.tomassirio.wanderer.query.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.Reactions;
import com.tomassirio.wanderer.commons.domain.WeatherCondition;
import com.tomassirio.wanderer.commons.dto.TripUpdateDTO;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import com.tomassirio.wanderer.query.service.TripUpdateService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class TripUpdateQueryControllerTest {

    private static final String TRIP_UPDATE_BY_ID_URL = "/api/1/trips/updates/{id}";
    private static final String TRIP_UPDATES_FOR_TRIP_URL = "/api/1/trips/{tripId}/updates";

    private MockMvc mockMvc;

    @Mock private TripUpdateService tripUpdateService;

    @InjectMocks private TripUpdateQueryController tripUpdateQueryController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        tripUpdateQueryController, new GlobalExceptionHandler());
    }

    @Test
    void getTripUpdate_whenTripUpdateExists_shouldReturnTripUpdate() throws Exception {
        // Given
        UUID tripUpdateId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        TripUpdateDTO tripUpdate = createTripUpdateDTO(tripUpdateId, tripId, 85, "Great location!");

        when(tripUpdateService.getTripUpdate(tripUpdateId)).thenReturn(tripUpdate);

        // When & Then
        mockMvc.perform(get(TRIP_UPDATE_BY_ID_URL, tripUpdateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripUpdateId.toString()))
                .andExpect(jsonPath("$.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.location").exists())
                .andExpect(jsonPath("$.location.lat").value(42.3601))
                .andExpect(jsonPath("$.location.lon").value(-71.0589))
                .andExpect(jsonPath("$.battery").value(85))
                .andExpect(jsonPath("$.message").value("Great location!"))
                .andExpect(jsonPath("$.city").value("Santiago de Compostela"))
                .andExpect(jsonPath("$.country").value("Spain"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getTripUpdate_whenTripUpdateDoesNotExist_shouldReturnNotFound() throws Exception {
        // Given
        UUID nonExistentTripUpdateId = UUID.randomUUID();

        when(tripUpdateService.getTripUpdate(nonExistentTripUpdateId))
                .thenThrow(new EntityNotFoundException("Trip update not found"));

        // When & Then
        mockMvc.perform(get(TRIP_UPDATE_BY_ID_URL, nonExistentTripUpdateId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTripUpdate_whenTripUpdateHasNoMessage_shouldReturnTripUpdateWithNullMessage()
            throws Exception {
        // Given
        UUID tripUpdateId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        TripUpdateDTO tripUpdate = createTripUpdateDTO(tripUpdateId, tripId, 90, null);

        when(tripUpdateService.getTripUpdate(tripUpdateId)).thenReturn(tripUpdate);

        // When & Then
        mockMvc.perform(get(TRIP_UPDATE_BY_ID_URL, tripUpdateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripUpdateId.toString()))
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    void getTripUpdate_whenTripUpdateHasLowBattery_shouldReturnCorrectBatteryLevel()
            throws Exception {
        // Given
        UUID tripUpdateId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        TripUpdateDTO tripUpdate = createTripUpdateDTO(tripUpdateId, tripId, 15, "Low battery!");

        when(tripUpdateService.getTripUpdate(tripUpdateId)).thenReturn(tripUpdate);

        // When & Then
        mockMvc.perform(get(TRIP_UPDATE_BY_ID_URL, tripUpdateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.battery").value(15))
                .andExpect(jsonPath("$.message").value("Low battery!"));
    }

    @Test
    void getTripUpdatesForTrip_whenTripUpdatesExist_shouldReturnTripUpdates() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID updateId1 = UUID.randomUUID();
        UUID updateId2 = UUID.randomUUID();
        UUID updateId3 = UUID.randomUUID();

        TripUpdateDTO update1 = createTripUpdateDTO(updateId1, tripId, 90, "First update");
        TripUpdateDTO update2 = createTripUpdateDTO(updateId2, tripId, 85, "Second update");
        TripUpdateDTO update3 = createTripUpdateDTO(updateId3, tripId, 80, "Third update");

        when(tripUpdateService.getTripUpdatesForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(update1, update2, update3)));

        // When & Then
        mockMvc.perform(get(TRIP_UPDATES_FOR_TRIP_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].id").value(updateId1.toString()))
                .andExpect(jsonPath("$.content[0].message").value("First update"))
                .andExpect(jsonPath("$.content[0].battery").value(90))
                .andExpect(jsonPath("$.content[0].city").value("Santiago de Compostela"))
                .andExpect(jsonPath("$.content[0].country").value("Spain"))
                .andExpect(jsonPath("$.content[1].id").value(updateId2.toString()))
                .andExpect(jsonPath("$.content[1].message").value("Second update"))
                .andExpect(jsonPath("$.content[1].battery").value(85))
                .andExpect(jsonPath("$.content[2].id").value(updateId3.toString()))
                .andExpect(jsonPath("$.content[2].message").value("Third update"))
                .andExpect(jsonPath("$.content[2].battery").value(80));
    }

    @Test
    void getTripUpdatesForTrip_whenNoTripUpdatesExist_shouldReturnEmptyPage() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        when(tripUpdateService.getTripUpdatesForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // When & Then
        mockMvc.perform(get(TRIP_UPDATES_FOR_TRIP_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getTripUpdatesForTrip_whenTripDoesNotExist_shouldReturnNotFound() throws Exception {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();

        when(tripUpdateService.getTripUpdatesForTrip(eq(nonExistentTripId), any(Pageable.class)))
                .thenThrow(new EntityNotFoundException("Trip not found"));

        // When & Then
        mockMvc.perform(get(TRIP_UPDATES_FOR_TRIP_URL, nonExistentTripId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTripUpdatesForTrip_shouldReturnUpdatesInDescendingOrder() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant earlier = now.minusSeconds(3600);
        Instant earliest = now.minusSeconds(7200);

        UUID updateId1 = UUID.randomUUID();
        UUID updateId2 = UUID.randomUUID();
        UUID updateId3 = UUID.randomUUID();

        TripUpdateDTO recentUpdate =
                createTripUpdateDTOWithTimestamp(updateId1, tripId, 90, "Recent", now);
        TripUpdateDTO middleUpdate =
                createTripUpdateDTOWithTimestamp(updateId2, tripId, 85, "Middle", earlier);
        TripUpdateDTO oldUpdate =
                createTripUpdateDTOWithTimestamp(updateId3, tripId, 80, "Old", earliest);

        when(tripUpdateService.getTripUpdatesForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(recentUpdate, middleUpdate, oldUpdate)));

        // When & Then
        mockMvc.perform(get(TRIP_UPDATES_FOR_TRIP_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].message").value("Recent"))
                .andExpect(jsonPath("$.content[1].message").value("Middle"))
                .andExpect(jsonPath("$.content[2].message").value("Old"));
    }

    @Test
    void getTripUpdatesForTrip_whenUpdatesHaveVariousBatteryLevels_shouldReturnAllLevels()
            throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID updateId1 = UUID.randomUUID();
        UUID updateId2 = UUID.randomUUID();
        UUID updateId3 = UUID.randomUUID();
        UUID updateId4 = UUID.randomUUID();

        TripUpdateDTO update1 = createTripUpdateDTO(updateId1, tripId, 100, "Full battery");
        TripUpdateDTO update2 = createTripUpdateDTO(updateId2, tripId, 50, "Half battery");
        TripUpdateDTO update3 = createTripUpdateDTO(updateId3, tripId, 10, "Low battery");
        TripUpdateDTO update4 = createTripUpdateDTO(updateId4, tripId, null, "No battery info");

        when(tripUpdateService.getTripUpdatesForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(update1, update2, update3, update4)));

        // When & Then
        mockMvc.perform(get(TRIP_UPDATES_FOR_TRIP_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].battery").value(100))
                .andExpect(jsonPath("$.content[1].battery").value(50))
                .andExpect(jsonPath("$.content[2].battery").value(10))
                .andExpect(jsonPath("$.content[3].battery").isEmpty());
    }

    @Test
    void getTripUpdatesForTrip_whenUpdatesHaveDifferentLocations_shouldReturnAllLocations()
            throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID updateId1 = UUID.randomUUID();
        UUID updateId2 = UUID.randomUUID();

        GeoLocation location1 = GeoLocation.builder().lat(42.3601).lon(-71.0589).build();
        GeoLocation location2 = GeoLocation.builder().lat(40.7128).lon(-74.0060).build();

        TripUpdateDTO update1 =
                new TripUpdateDTO(
                        updateId1.toString(),
                        tripId.toString(),
                        location1,
                        85,
                        "Boston",
                        new Reactions(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.now());

        TripUpdateDTO update2 =
                new TripUpdateDTO(
                        updateId2.toString(),
                        tripId.toString(),
                        location2,
                        80,
                        "New York",
                        new Reactions(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.now());

        when(tripUpdateService.getTripUpdatesForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(update1, update2)));

        // When & Then
        mockMvc.perform(get(TRIP_UPDATES_FOR_TRIP_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].location.lat").value(42.3601))
                .andExpect(jsonPath("$.content[0].location.lon").value(-71.0589))
                .andExpect(jsonPath("$.content[0].message").value("Boston"))
                .andExpect(jsonPath("$.content[1].location.lat").value(40.7128))
                .andExpect(jsonPath("$.content[1].location.lon").value(-74.0060))
                .andExpect(jsonPath("$.content[1].message").value("New York"));
    }

    // Helper methods

    private TripUpdateDTO createTripUpdateDTO(
            UUID tripUpdateId, UUID tripId, Integer battery, String message) {
        GeoLocation location = GeoLocation.builder().lat(42.3601).lon(-71.0589).build();

        return new TripUpdateDTO(
                tripUpdateId.toString(),
                tripId.toString(),
                location,
                battery,
                message,
                new Reactions(),
                "Santiago de Compostela",
                "Spain",
                18.5,
                WeatherCondition.PARTLY_CLOUDY,
                null,
                null,
                Instant.now());
    }

    private TripUpdateDTO createTripUpdateDTOWithTimestamp(
            UUID tripUpdateId, UUID tripId, Integer battery, String message, Instant timestamp) {
        GeoLocation location = GeoLocation.builder().lat(42.3601).lon(-71.0589).build();

        return new TripUpdateDTO(
                tripUpdateId.toString(),
                tripId.toString(),
                location,
                battery,
                message,
                new Reactions(),
                "Santiago de Compostela",
                "Spain",
                18.5,
                WeatherCondition.PARTLY_CLOUDY,
                null,
                null,
                timestamp);
    }
}
