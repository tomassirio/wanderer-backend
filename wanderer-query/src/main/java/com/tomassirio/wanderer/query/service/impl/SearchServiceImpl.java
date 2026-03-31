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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    public SearchResultsResponse search(
            String searchTerm, Pageable userPageable, Pageable tripPageable) {
        log.info(
                "Performing unified search for term: '{}' (users: page={}, size={} | trips: page={}, size={})",
                searchTerm,
                userPageable.getPageNumber(),
                userPageable.getPageSize(),
                tripPageable.getPageNumber(),
                tripPageable.getPageSize());

        Page<UserSearchResult> users = searchUsers(searchTerm, userPageable);
        Page<TripSummaryDTO> trips = searchTrips(searchTerm, tripPageable);

        log.info(
                "Search completed. Found {} users (page {}/{}) and {} trips (page {}/{}) for term: '{}'",
                users.getTotalElements(),
                users.getNumber() + 1,
                users.getTotalPages(),
                trips.getTotalElements(),
                trips.getNumber() + 1,
                trips.getTotalPages(),
                searchTerm);

        return new SearchResultsResponse(users, trips);
    }

    private Page<UserSearchResult> searchUsers(String searchTerm, Pageable pageable) {
        Page<UserSummaryDto> userPage = userRepository.searchUserSummaries(searchTerm, pageable);

        List<UserSearchResult> userResults =
                userPage.getContent().stream()
                        .map(
                                user ->
                                        new UserSearchResult(
                                                user.getId(),
                                                user.getUsername(),
                                                user.getDisplayName(),
                                                ThumbnailUrlService
                                                        .generateUserProfileThumbnailUrl(
                                                                user.getId())))
                        .toList();

        return new PageImpl<>(userResults, pageable, userPage.getTotalElements());
    }

    private Page<TripSummaryDTO> searchTrips(String searchTerm, Pageable pageable) {
        Page<Trip> tripPage =
                tripRepository.searchPublicTripsByName(
                        searchTerm,
                        TripVisibility.PUBLIC,
                        TripStatus.getActiveStatuses(),
                        pageable);

        List<TripSummaryDTO> enriched =
                tripEnrichmentHelper.enrichTripsToSummaries(tripPage.getContent());

        return new PageImpl<>(enriched, pageable, tripPage.getTotalElements());
    }
}
