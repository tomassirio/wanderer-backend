package com.tomassirio.wanderer.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import com.tomassirio.wanderer.query.dto.SearchResultsResponse;
import com.tomassirio.wanderer.query.dto.UserSearchResult;
import com.tomassirio.wanderer.query.dto.UserSummaryDto;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.helper.TripEnrichmentHelper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock private UserRepository userRepository;

    @Mock private TripRepository tripRepository;

    @Mock private TripEnrichmentHelper tripEnrichmentHelper;

    @InjectMocks private SearchServiceImpl searchService;

    private UUID userId;
    private UUID tripId;
    private UUID tripPlanId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        tripPlanId = UUID.randomUUID();
    }

    @Test
    void search_shouldReturnUsersAndTrips() {
        String searchTerm = "camino";
        int limit = 5;

        // Mock user search
        UserSummaryDto userSummary = createUserSummary(userId, "caminoWalker", "Camino Walker");
        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(userSummary)));

        // Mock trip search
        Trip trip = createTrip(tripId, userId, "Camino de Santiago");
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(List.of(trip));

        TripSummaryDTO tripSummary = createTripSummaryDTO(tripId, userId, "Camino de Santiago");
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of(trip)))
                .thenReturn(List.of(tripSummary));

        // Execute
        SearchResultsResponse result = searchService.search(searchTerm, limit);

        // Verify
        assertNotNull(result);
        assertEquals(1, result.users().size());
        assertEquals(1, result.trips().size());

        UserSearchResult userResult = result.users().get(0);
        assertEquals(userId, userResult.id());
        assertEquals("caminoWalker", userResult.username());
        assertEquals("Camino Walker", userResult.displayName());

        assertEquals(tripSummary, result.trips().get(0));
    }

    @Test
    void search_shouldReturnEmptyResults_whenNothingMatches() {
        String searchTerm = "nonexistent";
        int limit = 10;

        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(Page.empty());
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(List.of());
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        assertNotNull(result);
        assertTrue(result.users().isEmpty());
        assertTrue(result.trips().isEmpty());
    }

    @Test
    void search_shouldReturnOnlyUsers_whenNoTripsMatch() {
        String searchTerm = "walker";
        int limit = 5;

        UserSummaryDto userSummary = createUserSummary(userId, "walker123", "The Walker");
        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(userSummary)));
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(List.of());
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        assertNotNull(result);
        assertEquals(1, result.users().size());
        assertTrue(result.trips().isEmpty());
        assertEquals("walker123", result.users().get(0).username());
    }

    @Test
    void search_shouldReturnOnlyTrips_whenNoUsersMatch() {
        String searchTerm = "santiago";
        int limit = 5;

        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(Page.empty());

        Trip trip = createTrip(tripId, userId, "Santiago Trail");
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(List.of(trip));

        TripSummaryDTO tripSummary = createTripSummaryDTO(tripId, userId, "Santiago Trail");
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of(trip)))
                .thenReturn(List.of(tripSummary));

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        assertNotNull(result);
        assertTrue(result.users().isEmpty());
        assertEquals(1, result.trips().size());
        assertEquals("Santiago Trail", result.trips().get(0).name());
    }

    @Test
    void search_shouldReturnMultipleUsersAndTrips() {
        String searchTerm = "test";
        int limit = 10;

        UUID userId2 = UUID.randomUUID();
        UUID tripId2 = UUID.randomUUID();

        UserSummaryDto userSummary1 = createUserSummary(userId, "testUser1", "Test User One");
        UserSummaryDto userSummary2 = createUserSummary(userId2, "testUser2", "Test User Two");
        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(userSummary1, userSummary2)));

        Trip trip1 = createTrip(tripId, userId, "Test Trip 1");
        Trip trip2 = createTrip(tripId2, userId2, "Test Trip 2");
        List<Trip> trips = List.of(trip1, trip2);
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(trips);

        TripSummaryDTO summary1 = createTripSummaryDTO(tripId, userId, "Test Trip 1");
        TripSummaryDTO summary2 = createTripSummaryDTO(tripId2, userId2, "Test Trip 2");
        when(tripEnrichmentHelper.enrichTripsToSummaries(trips))
                .thenReturn(List.of(summary1, summary2));

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        assertNotNull(result);
        assertEquals(2, result.users().size());
        assertEquals(2, result.trips().size());
    }

    @Test
    void search_shouldRespectLimit() {
        String searchTerm = "trail";
        int limit = 1;

        UserSummaryDto userSummary = createUserSummary(userId, "trail_fan", "Trail Fan");
        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(userSummary)));

        Trip trip = createTrip(tripId, userId, "Mountain Trail");
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(List.of(trip));

        TripSummaryDTO tripSummary = createTripSummaryDTO(tripId, userId, "Mountain Trail");
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of(trip)))
                .thenReturn(List.of(tripSummary));

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        assertNotNull(result);
        // Verify PageRequest was constructed with the correct limit
        verify(userRepository).searchUserSummaries(eq(searchTerm), eq(PageRequest.of(0, 1)));
        verify(tripRepository)
                .searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(PageRequest.of(0, 1)));
    }

    @Test
    void search_shouldGenerateAvatarUrlForUsers() {
        String searchTerm = "pilgrim";
        int limit = 5;

        UserSummaryDto userSummary = createUserSummary(userId, "pilgrim", "The Pilgrim");
        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(userSummary)));
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(List.of());
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        assertNotNull(result);
        assertEquals(1, result.users().size());
        UserSearchResult userResult = result.users().get(0);
        assertNotNull(userResult.avatarUrl());
        assertTrue(userResult.avatarUrl().contains(userId.toString()));
        assertTrue(userResult.avatarUrl().contains("profiles"));
    }

    @Test
    void search_shouldDelegateEnrichmentToHelper() {
        String searchTerm = "camino";
        int limit = 5;

        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(Page.empty());

        Trip trip = createTrip(tripId, userId, "Camino Trip");
        List<Trip> trips = List.of(trip);
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(trips);

        TripSummaryDTO summary =
                createPromotedTripSummaryDTO(tripId, userId, "Camino Trip", Instant.now());
        when(tripEnrichmentHelper.enrichTripsToSummaries(trips)).thenReturn(List.of(summary));

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        // Verify the enrichment helper was called with the raw trips
        verify(tripEnrichmentHelper).enrichTripsToSummaries(trips);
        assertEquals(1, result.trips().size());
        assertTrue(result.trips().get(0).isPromoted());
    }

    @Test
    void search_shouldHandleUserWithNullDisplayName() {
        String searchTerm = "anon";
        int limit = 5;

        UserSummaryDto userSummary = createUserSummary(userId, "anon_user", null);
        PageRequest pageRequest = PageRequest.of(0, limit);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(userSummary)));
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(pageRequest)))
                .thenReturn(List.of());
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result = searchService.search(searchTerm, limit);

        assertNotNull(result);
        assertEquals(1, result.users().size());
        assertEquals("anon_user", result.users().get(0).username());
        assertNull(result.users().get(0).displayName());
    }

    // --- Helper methods ---

    private Trip createTrip(UUID tripId, UUID userId, String name) {
        TripSettings tripSettings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .build();

        TripDetails tripDetails =
                TripDetails.builder()
                        .startTimestamp(Instant.now())
                        .currentDay(5)
                        .waypoints(new ArrayList<>())
                        .build();

        return Trip.builder()
                .id(tripId)
                .name(name)
                .userId(userId)
                .tripSettings(tripSettings)
                .tripDetails(tripDetails)
                .tripPlanId(tripPlanId)
                .creationTimestamp(Instant.now())
                .enabled(true)
                .build();
    }

    private TripSummaryDTO createTripSummaryDTO(UUID tripId, UUID userId, String name) {
        return new TripSummaryDTO(
                tripId.toString(),
                name,
                userId.toString(),
                "testUser",
                null,
                Instant.now(),
                0,
                5,
                tripPlanId.toString(),
                false,
                null,
                false,
                null);
    }

    private TripSummaryDTO createPromotedTripSummaryDTO(
            UUID tripId, UUID userId, String name, Instant promotedAt) {
        return new TripSummaryDTO(
                tripId.toString(),
                name,
                userId.toString(),
                "testUser",
                null,
                Instant.now(),
                0,
                5,
                tripPlanId.toString(),
                true,
                promotedAt,
                false,
                null);
    }

    private UserSummaryDto createUserSummary(UUID id, String username, String displayName) {
        return new UserSummaryDto() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public String getProfilePictureUrl() {
                return "/thumbnails/profiles/" + id + ".png";
            }
        };
    }
}


