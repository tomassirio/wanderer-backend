package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import com.tomassirio.wanderer.query.dto.SearchResultsResponse;
import com.tomassirio.wanderer.query.dto.UserSearchResult;
import com.tomassirio.wanderer.query.dto.UserSummaryDto;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.SearchService;
import com.tomassirio.wanderer.query.service.helper.TripEnrichmentHelper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Implementation of SearchService for unified search operations.
 *
 * @since 1.2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripEnrichmentHelper tripEnrichmentHelper;

    @Override
    public SearchResultsResponse search(String searchTerm, int limit) {
        log.info("Performing unified search for term: '{}' with limit: {}", searchTerm, limit);

        List<UserSearchResult> users = searchUsers(searchTerm, limit);
        List<TripSummaryDTO> trips = searchTrips(searchTerm, limit);

        log.info(
                "Search completed. Found {} users and {} trips for term: '{}'",
                users.size(),
                trips.size(),
                searchTerm);

        return new SearchResultsResponse(users, trips);
    }

    private List<UserSearchResult> searchUsers(String searchTerm, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        List<UserSummaryDto> userSummaries =
                userRepository.searchUserSummaries(searchTerm, pageRequest).getContent();

        return userSummaries.stream()
                .map(
                        user ->
                                new UserSearchResult(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getDisplayName(),
                                        ThumbnailUrlService.generateUserProfileThumbnailUrl(
                                                user.getId())))
                .toList();
    }

    private List<TripSummaryDTO> searchTrips(String searchTerm, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        List<Trip> trips =
                tripRepository.searchPublicTripsByName(
                        searchTerm,
                        TripVisibility.PUBLIC,
                        TripStatus.getActiveStatuses(),
                        pageRequest);

        return tripEnrichmentHelper.enrichTripsToSummaries(trips);
    }
}
