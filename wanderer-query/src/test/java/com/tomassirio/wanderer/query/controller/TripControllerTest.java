package com.tomassirio.wanderer.query.controller;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USERNAME;
import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import com.tomassirio.wanderer.commons.dto.TripDetailsDTO;
import com.tomassirio.wanderer.commons.dto.TripSettingsDTO;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import com.tomassirio.wanderer.query.service.TripService;
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
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class TripControllerTest {

    private static final String TRIPS_BASE_URL = "/api/1/trips";
    private static final String TRIPS_ME_URL = TRIPS_BASE_URL + "/me";

    private MockMvc mockMvc;

    @Mock private TripService tripService;

    @InjectMocks private TripController tripController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        tripController, new GlobalExceptionHandler());
    }

    @Test
    void getTrip_whenTripExists_shouldReturnTrip() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripDTO trip = createTripDTO(tripId, "Summer Road Trip", TripVisibility.PUBLIC);

        when(tripService.getTrip(tripId)).thenReturn(trip);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/{id}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId.toString()))
                .andExpect(jsonPath("$.name").value("Summer Road Trip"))
                .andExpect(jsonPath("$.tripSettings.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.tripSettings.tripStatus").value("CREATED"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void getTrip_whenTripDoesNotExist_shouldReturnNotFound() throws Exception {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();

        when(tripService.getTrip(nonExistentTripId))
                .thenThrow(new EntityNotFoundException("Trip not found"));

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/{id}", nonExistentTripId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTrip_whenTripIsPrivate_shouldReturnPrivateTrip() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripDTO trip = createTripDTO(tripId, "Private Trip", TripVisibility.PRIVATE);

        when(tripService.getTrip(tripId)).thenReturn(trip);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/{id}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId.toString()))
                .andExpect(jsonPath("$.name").value("Private Trip"))
                .andExpect(jsonPath("$.tripSettings.visibility").value("PRIVATE"));
    }

    @Test
    void getTrip_whenTripHasNoTimestamps_shouldReturnTripWithNullTimestamps() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        TripDTO trip = createTripDTO(tripId, "New Trip", TripVisibility.PUBLIC);

        when(tripService.getTrip(tripId)).thenReturn(trip);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/{id}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId.toString()))
                .andExpect(jsonPath("$.name").value("New Trip"))
                .andExpect(jsonPath("$.tripDetails.startTimestamp").isEmpty())
                .andExpect(jsonPath("$.tripDetails.endTimestamp").isEmpty());
    }

    @Test
    void getAllTrips_whenTripsExist_shouldReturnListOfTrips() throws Exception {
        // Given
        UUID tripId1 = UUID.randomUUID();
        UUID tripId2 = UUID.randomUUID();

        TripDTO trip1 = createTripDTO(tripId1, "Trip 1", TripVisibility.PUBLIC);
        TripDTO trip2 = createTripDTO(tripId2, "Trip 2", TripVisibility.PRIVATE);

        when(tripService.getAllTrips()).thenReturn(List.of(trip1, trip2));

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(tripId1.toString()))
                .andExpect(jsonPath("$[0].name").value("Trip 1"))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[1].id").value(tripId2.toString()))
                .andExpect(jsonPath("$[1].name").value("Trip 2"))
                .andExpect(jsonPath("$[1].tripSettings.visibility").value("PRIVATE"));
    }

    @Test
    void getAllTrips_whenNoTripsExist_shouldReturnEmptyList() throws Exception {
        // Given
        when(tripService.getAllTrips()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllTrips_withMultipleTrips_shouldReturnAllTrips() throws Exception {
        // Given
        List<TripDTO> trips =
                List.of(
                        createTripDTO(UUID.randomUUID(), "Trip A", TripVisibility.PUBLIC),
                        createTripDTO(UUID.randomUUID(), "Trip B", TripVisibility.PRIVATE),
                        createTripDTO(UUID.randomUUID(), "Trip C", TripVisibility.PROTECTED),
                        createTripDTO(UUID.randomUUID(), "Trip D", TripVisibility.PUBLIC),
                        createTripDTO(UUID.randomUUID(), "Trip E", TripVisibility.PUBLIC));

        when(tripService.getAllTrips()).thenReturn(trips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void getMyTrips_whenTripsExist_shouldReturnListOfTrips() throws Exception {
        // Given
        List<TripDTO> trips =
                List.of(createTripDTO(UUID.randomUUID(), "My Trip", TripVisibility.PUBLIC));
        when(tripService.getTripsForUser(USER_ID)).thenReturn(trips);

        // When & Then
        mockMvc.perform(get(TRIPS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("My Trip"));
    }

    @Test
    void getMyTrips_whenNoTripsExist_shouldReturnEmptyList() throws Exception {
        // Given
        when(tripService.getTripsForUser(USER_ID)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIPS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMyTrips_withMultipleTrips_shouldReturnAllMyTrips() throws Exception {
        // Given
        List<TripDTO> trips =
                List.of(
                        createTripDTO(UUID.randomUUID(), "My Trip 1", TripVisibility.PUBLIC),
                        createTripDTO(UUID.randomUUID(), "My Trip 2", TripVisibility.PRIVATE),
                        createTripDTO(UUID.randomUUID(), "My Trip 3", TripVisibility.PROTECTED));
        when(tripService.getTripsForUser(USER_ID)).thenReturn(trips);

        // When & Then
        mockMvc.perform(get(TRIPS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("My Trip 1"))
                .andExpect(jsonPath("$[1].name").value("My Trip 2"))
                .andExpect(jsonPath("$[2].name").value("My Trip 3"));
    }

    @Test
    void getTripsByUser_whenTripsExist_shouldReturnVisibleTrips() throws Exception {
        // Given
        UUID otherUserId = UUID.randomUUID();
        List<TripDTO> trips =
                List.of(
                        createTripDTO(UUID.randomUUID(), "Public Trip", TripVisibility.PUBLIC),
                        createTripDTO(
                                UUID.randomUUID(), "Protected Trip", TripVisibility.PROTECTED));
        when(tripService.getTripsForUserWithVisibility(eq(otherUserId), any(UUID.class)))
                .thenReturn(trips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/users/{userId}", otherUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Public Trip"))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[1].name").value("Protected Trip"))
                .andExpect(jsonPath("$[1].tripSettings.visibility").value("PROTECTED"));
    }

    @Test
    void getTripsByUser_whenNoTripsExist_shouldReturnEmptyList() throws Exception {
        // Given
        UUID otherUserId = UUID.randomUUID();
        when(tripService.getTripsForUserWithVisibility(eq(otherUserId), any(UUID.class)))
                .thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/users/{userId}", otherUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getTripsByUser_shouldNotIncludePrivateTrips() throws Exception {
        // Given
        UUID otherUserId = UUID.randomUUID();
        List<TripDTO> trips =
                List.of(createTripDTO(UUID.randomUUID(), "Public Trip", TripVisibility.PUBLIC));
        when(tripService.getTripsForUserWithVisibility(eq(otherUserId), any(UUID.class)))
                .thenReturn(trips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/users/{userId}", otherUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PUBLIC"));
    }

    @Test
    void getOngoingPublicTrips_whenOngoingTripsExist_shouldReturnOngoingTrips() throws Exception {
        // Given
        List<TripDTO> ongoingTrips =
                List.of(
                        createTripDTOWithStatus(
                                UUID.randomUUID(),
                                "Ongoing Trip 1",
                                TripVisibility.PUBLIC,
                                TripStatus.IN_PROGRESS),
                        createTripDTOWithStatus(
                                UUID.randomUUID(),
                                "Ongoing Trip 2",
                                TripVisibility.PUBLIC,
                                TripStatus.IN_PROGRESS));
        when(tripService.getOngoingPublicTrips(any())).thenReturn(ongoingTrips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ongoing Trip 1"))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[0].tripSettings.tripStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].name").value("Ongoing Trip 2"))
                .andExpect(jsonPath("$[1].tripSettings.tripStatus").value("IN_PROGRESS"));
    }

    @Test
    void getOngoingPublicTrips_whenNoOngoingTripsExist_shouldReturnEmptyList() throws Exception {
        // Given
        when(tripService.getOngoingPublicTrips(any())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getOngoingPublicTrips_shouldOnlyReturnPublicTrips() throws Exception {
        // Given
        List<TripDTO> ongoingTrips =
                List.of(
                        createTripDTOWithStatus(
                                UUID.randomUUID(),
                                "Public Ongoing",
                                TripVisibility.PUBLIC,
                                TripStatus.IN_PROGRESS));
        when(tripService.getOngoingPublicTrips(any())).thenReturn(ongoingTrips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PUBLIC"));
    }

    @Test
    void getAllAvailableTrips_whenAvailableTripsExist_shouldReturnAllAvailableTrips()
            throws Exception {
        // Given
        List<TripDTO> availableTrips =
                List.of(
                        createTripDTO(UUID.randomUUID(), "My Trip", TripVisibility.PRIVATE),
                        createTripDTO(UUID.randomUUID(), "Public Trip", TripVisibility.PUBLIC),
                        createTripDTO(UUID.randomUUID(), "Friend Trip", TripVisibility.PROTECTED));
        when(tripService.getAllAvailableTripsForUser(any(UUID.class))).thenReturn(availableTrips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/me/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("My Trip"))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$[1].name").value("Public Trip"))
                .andExpect(jsonPath("$[1].tripSettings.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[2].name").value("Friend Trip"))
                .andExpect(jsonPath("$[2].tripSettings.visibility").value("PROTECTED"));
    }

    @Test
    void getAllAvailableTrips_whenNoAvailableTripsExist_shouldReturnEmptyList() throws Exception {
        // Given
        when(tripService.getAllAvailableTripsForUser(any(UUID.class))).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/me/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllAvailableTrips_shouldIncludeOwnPrivateTrips() throws Exception {
        // Given
        List<TripDTO> availableTrips =
                List.of(
                        createTripDTO(
                                UUID.randomUUID(), "My Private Trip", TripVisibility.PRIVATE));
        when(tripService.getAllAvailableTripsForUser(any(UUID.class))).thenReturn(availableTrips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/me/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("My Private Trip"))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PRIVATE"));
    }

    @Test
    void getAllAvailableTrips_shouldIncludePublicTripsFromOtherUsers() throws Exception {
        // Given
        List<TripDTO> availableTrips =
                List.of(
                        createTripDTO(
                                UUID.randomUUID(), "Other User Public", TripVisibility.PUBLIC));
        when(tripService.getAllAvailableTripsForUser(any(UUID.class))).thenReturn(availableTrips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/me/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Other User Public"))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PUBLIC"));
    }

    @Test
    void getAllAvailableTrips_shouldIncludeProtectedTripsFromFriends() throws Exception {
        // Given
        List<TripDTO> availableTrips =
                List.of(
                        createTripDTO(
                                UUID.randomUUID(), "Friend Protected", TripVisibility.PROTECTED));
        when(tripService.getAllAvailableTripsForUser(any(UUID.class))).thenReturn(availableTrips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/me/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Friend Protected"))
                .andExpect(jsonPath("$[0].tripSettings.visibility").value("PROTECTED"));
    }

    @Test
    void getAllAvailableTrips_withMultipleVisibilityTypes_shouldReturnAllTypes() throws Exception {
        // Given
        List<TripDTO> availableTrips =
                List.of(
                        createTripDTO(UUID.randomUUID(), "Trip 1", TripVisibility.PUBLIC),
                        createTripDTO(UUID.randomUUID(), "Trip 2", TripVisibility.PRIVATE),
                        createTripDTO(UUID.randomUUID(), "Trip 3", TripVisibility.PROTECTED),
                        createTripDTO(UUID.randomUUID(), "Trip 4", TripVisibility.PUBLIC),
                        createTripDTO(UUID.randomUUID(), "Trip 5", TripVisibility.PROTECTED));
        when(tripService.getAllAvailableTripsForUser(any(UUID.class))).thenReturn(availableTrips);

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/me/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void getAllAvailableTrips_shouldCallServiceWithCorrectUserId() throws Exception {
        // Given
        when(tripService.getAllAvailableTripsForUser(USER_ID)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get(TRIPS_BASE_URL + "/me/available")).andExpect(status().isOk());

        verify(tripService).getAllAvailableTripsForUser(USER_ID);
    }

    private TripDTO createTripDTO(UUID tripId, String name, TripVisibility visibility) {
        TripSettingsDTO tripSettings =
                new TripSettingsDTO(TripStatus.CREATED, visibility, null, null, null);
        TripDetailsDTO tripDetails = new TripDetailsDTO(null, null, null, null, null, null);

        return new TripDTO(
                tripId.toString(),
                name,
                USER_ID.toString(),
                USERNAME, // username
                tripSettings,
                tripDetails,
                null, // tripPlanId
                List.of(), // comments
                List.of(), // tripUpdates
                List.of(), // tripDays
                null, // encodedPolyline
                null, // plannedPolyline
                null, // polylineUpdatedAt
                Instant.now(),
                true);
    }

    private TripDTO createTripDTOWithStatus(
            UUID tripId, String name, TripVisibility visibility, TripStatus status) {
        TripSettingsDTO tripSettings = new TripSettingsDTO(status, visibility, null, null, null);
        TripDetailsDTO tripDetails = new TripDetailsDTO(null, null, null, null, null, null);

        return new TripDTO(
                tripId.toString(),
                name,
                USER_ID.toString(),
                USERNAME,
                tripSettings,
                tripDetails,
                null, // tripPlanId
                List.of(), // comments
                List.of(), // tripUpdates
                List.of(), // tripDays
                null, // encodedPolyline
                null, // plannedPolyline
                null, // polylineUpdatedAt
                Instant.now(),
                true);
    }
}
