package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.query.dto.SearchResultsResponse;
import com.tomassirio.wanderer.query.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for unified search operations. Handles search requests for users and trips.
 *
 * @since 1.2.0
 */
@RestController
@RequestMapping(
        value = ApiConstants.API_V1 + "/search",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Search", description = "Endpoints for searching users and trips")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(
            summary = "Search for users and trips",
            description =
                    "Performs a unified search for users and trips with independent pagination. "
                            + "Users are matched by username and display name. "
                            + "Trips are matched by name or owner username (partial or full match) "
                            + "and follow the same criteria as the /public endpoint "
                            + "(ongoing public trips with promoted status). "
                            + "Each category (users/trips) is paginated independently.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    public ResponseEntity<SearchResultsResponse> search(
            @Parameter(
                            description =
                                    "Search term to match against usernames, display names,"
                                            + " trip names, and trip owner usernames")
                    @RequestParam
                    String q,
            @Parameter(description = "Page number for user results (0-based). Default: 0")
                    @RequestParam(defaultValue = "0")
                    int userPage,
            @Parameter(description = "Page size for user results. Default: 10")
                    @RequestParam(defaultValue = "10")
                    int userSize,
            @Parameter(description = "Page number for trip results (0-based). Default: 0")
                    @RequestParam(defaultValue = "0")
                    int tripPage,
            @Parameter(description = "Page size for trip results. Default: 10")
                    @RequestParam(defaultValue = "10")
                    int tripSize) {
        log.info(
                "Received search request with query: '{}' (users: page={}, size={} | trips: page={}, size={})",
                q,
                userPage,
                userSize,
                tripPage,
                tripSize);

        if (q == null || q.trim().isEmpty()) {
            log.warn("Empty search query received");
            return ResponseEntity.ok(
                    new SearchResultsResponse(Page.empty(), Page.empty()));
        }

        SearchResultsResponse results =
                searchService.search(
                        q.trim(),
                        PageRequest.of(userPage, userSize),
                        PageRequest.of(tripPage, tripSize));

        log.info(
                "Search completed for query: '{}' - Found {} users (page {}/{}) and {} trips (page {}/{})",
                q,
                results.users().getTotalElements(),
                results.users().getNumber() + 1,
                results.users().getTotalPages(),
                results.trips().getTotalElements(),
                results.trips().getNumber() + 1,
                results.trips().getTotalPages());

        return ResponseEntity.ok(results);
    }
}
