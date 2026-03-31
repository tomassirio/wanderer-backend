package com.tomassirio.wanderer.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock private UserRepository userRepository;

    @Mock private TripRepository tripRepository;

    @Mock private TripEnrichmentHelper tripEnrichmentHelper;

    @InjectMocks private SearchServiceImpl searchService;

    private UUID userId;
    private UUID tripId;
    private UUID tripPlanId;

    private static final Pageable DEFAULT_USER_PAGEABLE = PageRequest.of(0, 10);
    private static final Pageable DEFAULT_TRIP_PAGEABLE = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        tripPlanId = UUID.randomUUID();
    }

    @Test
    void search_shouldReturnUsersAndTrips() {
        String searchTerm = "camino";

        UserSummaryDto userSummary = createUserSummary(userId, "caminoWalker", "Camino Walker");
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(userSummary), DEFAULT_USER_PAGEABLE, 1));

        Trip trip = createTrip(tripId, userId, "Camino de Santiago");
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(trip), DEFAULT_TRIP_PAGEABLE, 1));

        TripSummaryDTO tripSummary = createTripSummaryDTO(tripId, userId, "Camino de Santiago");
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of(trip)))
                .thenReturn(List.of(tripSummary));

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(1, result.users().getTotalElements());
        assertEquals(1, result.trips().getTotalElements());

        UserSearchResult userResult = result.users().getContent().getFirst();
        assertEquals(userId, userResult.id());
        assertEquals("caminoWalker", userResult.username());
        assertEquals("Camino Walker", userResult.displayName());

        assertEquals(tripSummary, result.trips().getContent().getFirst());
    }

    @Test
    void search_shouldReturnEmptyResults_whenNothingMatches() {
        String searchTerm = "nonexistent";

        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(Page.empty(DEFAULT_USER_PAGEABLE));
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(Page.empty(DEFAULT_TRIP_PAGEABLE));
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(0, result.users().getTotalElements());
        assertEquals(0, result.trips().getTotalElements());
        assertTrue(result.users().getContent().isEmpty());
        assertTrue(result.trips().getContent().isEmpty());
    }

    @Test
    void search_shouldReturnOnlyUsers_whenNoTripsMatch() {
        String searchTerm = "walker";

        UserSummaryDto userSummary = createUserSummary(userId, "walker123", "The Walker");
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(userSummary), DEFAULT_USER_PAGEABLE, 1));
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(Page.empty(DEFAULT_TRIP_PAGEABLE));
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(1, result.users().getTotalElements());
        assertEquals(0, result.trips().getTotalElements());
        assertEquals("walker123", result.users().getContent().getFirst().username());
    }

    @Test
    void search_shouldReturnOnlyTrips_whenNoUsersMatch() {
        String searchTerm = "santiago";

        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(Page.empty(DEFAULT_USER_PAGEABLE));

        Trip trip = createTrip(tripId, userId, "Santiago Trail");
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(trip), DEFAULT_TRIP_PAGEABLE, 1));

        TripSummaryDTO tripSummary = createTripSummaryDTO(tripId, userId, "Santiago Trail");
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of(trip)))
                .thenReturn(List.of(tripSummary));

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(0, result.users().getTotalElements());
        assertEquals(1, result.trips().getTotalElements());
        assertEquals("Santiago Trail", result.trips().getContent().getFirst().name());
    }

    @Test
    void search_shouldReturnMultipleUsersAndTrips() {
        String searchTerm = "test";

        UUID userId2 = UUID.randomUUID();
        UUID tripId2 = UUID.randomUUID();

        UserSummaryDto userSummary1 = createUserSummary(userId, "testUser1", "Test User One");
        UserSummaryDto userSummary2 = createUserSummary(userId2, "testUser2", "Test User Two");
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(
                        new PageImpl<>(
                                List.of(userSummary1, userSummary2), DEFAULT_USER_PAGEABLE, 2));

        Trip trip1 = createTrip(tripId, userId, "Test Trip 1");
        Trip trip2 = createTrip(tripId2, userId2, "Test Trip 2");
        List<Trip> trips = List.of(trip1, trip2);
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(new PageImpl<>(trips, DEFAULT_TRIP_PAGEABLE, 2));

        TripSummaryDTO summary1 = createTripSummaryDTO(tripId, userId, "Test Trip 1");
        TripSummaryDTO summary2 = createTripSummaryDTO(tripId2, userId2, "Test Trip 2");
        when(tripEnrichmentHelper.enrichTripsToSummaries(trips))
                .thenReturn(List.of(summary1, summary2));

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(2, result.users().getTotalElements());
        assertEquals(2, result.trips().getTotalElements());
    }

    @Test
    void search_shouldUseIndependentPagination() {
        String searchTerm = "trail";
        Pageable userPageable = PageRequest.of(0, 1);
        Pageable tripPageable = PageRequest.of(0, 5);

        UserSummaryDto userSummary = createUserSummary(userId, "trail_fan", "Trail Fan");
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(userPageable)))
                .thenReturn(new PageImpl<>(List.of(userSummary), userPageable, 3));

        Trip trip = createTrip(tripId, userId, "Mountain Trail");
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(tripPageable)))
                .thenReturn(new PageImpl<>(List.of(trip), tripPageable, 1));

        TripSummaryDTO tripSummary = createTripSummaryDTO(tripId, userId, "Mountain Trail");
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of(trip)))
                .thenReturn(List.of(tripSummary));

        SearchResultsResponse result =
                searchService.search(searchTerm, userPageable, tripPageable);

        assertNotNull(result);
        // Users: page size 1, total 3 → has next
        assertEquals(3, result.users().getTotalElements());
        assertEquals(3, result.users().getTotalPages());
        assertEquals(1, result.users().getContent().size());
        assertTrue(result.users().hasNext());

        // Trips: page size 5, total 1 → no next
        assertEquals(1, result.trips().getTotalElements());
        assertEquals(1, result.trips().getTotalPages());
        assertEquals(1, result.trips().getContent().size());
        assertFalse(result.trips().hasNext());

        verify(userRepository).searchUserSummaries(eq(searchTerm), eq(userPageable));
        verify(tripRepository)
                .searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(tripPageable));
    }

    @Test
    void search_shouldPaginateUsersIndependentlyFromTrips() {
        String searchTerm = "camino";
        Pageable userPageable = PageRequest.of(2, 5);
        Pageable tripPageable = PageRequest.of(0, 10);

        when(userRepository.searchUserSummaries(eq(searchTerm), eq(userPageable)))
                .thenReturn(Page.empty(userPageable));

        Trip trip = createTrip(tripId, userId, "Camino Trip");
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(tripPageable)))
                .thenReturn(new PageImpl<>(List.of(trip), tripPageable, 1));

        TripSummaryDTO summary = createTripSummaryDTO(tripId, userId, "Camino Trip");
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of(trip)))
                .thenReturn(List.of(summary));

        SearchResultsResponse result =
                searchService.search(searchTerm, userPageable, tripPageable);

        // User page 2 is empty but trips page 0 has results
        assertTrue(result.users().getContent().isEmpty());
        assertEquals(1, result.trips().getContent().size());
        assertEquals(2, result.users().getNumber());
        assertEquals(0, result.trips().getNumber());
    }

    @Test
    void search_shouldGenerateAvatarUrlForUsers() {
        String searchTerm = "pilgrim";

        UserSummaryDto userSummary = createUserSummary(userId, "pilgrim", "The Pilgrim");
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(userSummary), DEFAULT_USER_PAGEABLE, 1));
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(Page.empty(DEFAULT_TRIP_PAGEABLE));
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(1, result.users().getTotalElements());
        UserSearchResult userResult = result.users().getContent().getFirst();
        assertNotNull(userResult.avatarUrl());
        assertTrue(userResult.avatarUrl().contains(userId.toString()));
        assertTrue(userResult.avatarUrl().contains("profiles"));
    }

    @Test
    void search_shouldDelegateEnrichmentToHelper() {
        String searchTerm = "camino";

        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(Page.empty(DEFAULT_USER_PAGEABLE));

        Trip trip = createTrip(tripId, userId, "Camino Trip");
        List<Trip> trips = List.of(trip);
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(new PageImpl<>(trips, DEFAULT_TRIP_PAGEABLE, 1));

        TripSummaryDTO summary =
                createPromotedTripSummaryDTO(tripId, userId, "Camino Trip", Instant.now());
        when(tripEnrichmentHelper.enrichTripsToSummaries(trips)).thenReturn(List.of(summary));

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        verify(tripEnrichmentHelper).enrichTripsToSummaries(trips);
        assertEquals(1, result.trips().getTotalElements());
        assertTrue(result.trips().getContent().getFirst().isPromoted());
    }

    @Test
    void search_shouldHandleUserWithNullDisplayName() {
        String searchTerm = "anon";

        UserSummaryDto userSummary = createUserSummary(userId, "anon_user", null);
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(userSummary), DEFAULT_USER_PAGEABLE, 1));
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(Page.empty(DEFAULT_TRIP_PAGEABLE));
        when(tripEnrichmentHelper.enrichTripsToSummaries(List.of())).thenReturn(List.of());

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(1, result.users().getTotalElements());
        assertEquals("anon_user", result.users().getContent().getFirst().username());
        assertNull(result.users().getContent().getFirst().displayName());
    }

    @Test
    void search_shouldReturnTripsMatchingOwnerUsername() {
        String searchTerm = "user123";

        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID tripId1 = UUID.randomUUID();
        UUID tripId2 = UUID.randomUUID();

        UserSummaryDto user1 = createUserSummary(userId1, "user123", "User 123");
        UserSummaryDto user2 = createUserSummary(userId2, "user12345", "User 12345");
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(DEFAULT_USER_PAGEABLE)))
                .thenReturn(
                        new PageImpl<>(List.of(user1, user2), DEFAULT_USER_PAGEABLE, 2));

        Trip trip1 = createTrip(tripId1, userId1, "My Awesome Trip");
        Trip trip2 = createTrip(tripId2, userId2, "Another Great Journey");
        List<Trip> trips = List.of(trip1, trip2);
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(DEFAULT_TRIP_PAGEABLE)))
                .thenReturn(new PageImpl<>(trips, DEFAULT_TRIP_PAGEABLE, 2));

        TripSummaryDTO summary1 = createTripSummaryDTO(tripId1, userId1, "My Awesome Trip");
        TripSummaryDTO summary2 =
                createTripSummaryDTO(tripId2, userId2, "Another Great Journey");
        when(tripEnrichmentHelper.enrichTripsToSummaries(trips))
                .thenReturn(List.of(summary1, summary2));

        SearchResultsResponse result =
                searchService.search(searchTerm, DEFAULT_USER_PAGEABLE, DEFAULT_TRIP_PAGEABLE);

        assertNotNull(result);
        assertEquals(2, result.users().getTotalElements());
        assertEquals(2, result.trips().getTotalElements());
        assertEquals("My Awesome Trip", result.trips().getContent().get(0).name());
        assertEquals("Another Great Journey", result.trips().getContent().get(1).name());
    }

    @Test
    void search_shouldReturnCorrectPaginationMetadata() {
        String searchTerm = "popular";
        Pageable userPageable = PageRequest.of(1, 2);
        Pageable tripPageable = PageRequest.of(0, 3);

        // User page 1 with 2 items, total 5 → 3 pages
        UserSummaryDto user1 = createUserSummary(UUID.randomUUID(), "popular1", "Popular One");
        UserSummaryDto user2 = createUserSummary(UUID.randomUUID(), "popular2", "Popular Two");
        when(userRepository.searchUserSummaries(eq(searchTerm), eq(userPageable)))
                .thenReturn(new PageImpl<>(List.of(user1, user2), userPageable, 5));

        // Trip page 0 with 2 items, total 2 → 1 page
        Trip trip1 = createTrip(UUID.randomUUID(), userId, "Popular Trip 1");
        Trip trip2 = createTrip(UUID.randomUUID(), userId, "Popular Trip 2");
        List<Trip> trips = List.of(trip1, trip2);
        when(tripRepository.searchPublicTripsByName(
                        eq(searchTerm),
                        eq(TripVisibility.PUBLIC),
                        eq(TripStatus.getActiveStatuses()),
                        eq(tripPageable)))
                .thenReturn(new PageImpl<>(trips, tripPageable, 2));

        TripSummaryDTO summary1 =
                createTripSummaryDTO(trip1.getId(), userId, "Popular Trip 1");
        TripSummaryDTO summary2 =
                createTripSummaryDTO(trip2.getId(), userId, "Popular Trip 2");
        when(tripEnrichmentHelper.enrichTripsToSummaries(trips))
                .thenReturn(List.of(summary1, summary2));

        SearchResultsResponse result =
                searchService.search(searchTerm, userPageable, tripPageable);

        // Verify user pagination metadata
        assertEquals(1, result.users().getNumber());
        assertEquals(2, result.users().getSize());
        assertEquals(5, result.users().getTotalElements());
        assertEquals(3, result.users().getTotalPages());
        assertTrue(result.users().hasNext());
        assertTrue(result.users().hasPrevious());

        // Verify trip pagination metadata
        assertEquals(0, result.trips().getNumber());
        assertEquals(3, result.trips().getSize());
        assertEquals(2, result.trips().getTotalElements());
        assertEquals(1, result.trips().getTotalPages());
        assertFalse(result.trips().hasNext());
        assertFalse(result.trips().hasPrevious());
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
                0, // commentsCount
                5, // currentDay
                tripPlanId.toString(),
                0, // updateCount
                false, // isPromoted
                null, // promotedAt
                false, // isPreAnnounced
                null); // countdownStartDate
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
                0, // commentsCount
                5, // currentDay
                tripPlanId.toString(),
                0, // updateCount
                true, // isPromoted
                promotedAt,
                false, // isPreAnnounced
                null); // countdownStartDate
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

