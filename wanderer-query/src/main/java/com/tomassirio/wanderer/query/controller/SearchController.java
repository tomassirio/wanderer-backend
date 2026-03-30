package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.query.dto.SearchResultsResponse;
import com.tomassirio.wanderer.query.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping(value = ApiConstants.API_V1 + "/search", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Search", description = "Endpoints for searching users and trips")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(
            summary = "Search for users and trips",
            description =
                    "Performs a unified search for users and trips. "
                            + "Users are matched by username and display name. "
                            + "Trips are matched by name and follow the same criteria as the /public endpoint "
                            + "(ongoing public trips with promoted status).")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    public ResponseEntity<SearchResultsResponse> search(
            @Parameter(description = "Search term to match against usernames, display names, and trip names")
                    @RequestParam String q,
            @Parameter(description = "Maximum number of results to return per category (users/trips). Default: 10")
                    @RequestParam(defaultValue = "10") int limit) {
        log.info("Received search request with query: '{}' and limit: {}", q, limit);

        if (q == null || q.trim().isEmpty()) {
            log.warn("Empty search query received");
            return ResponseEntity.ok(new SearchResultsResponse(List.of(), List.of()));
        }

        SearchResultsResponse results = searchService.search(q.trim(), limit);

        log.info(
                "Search completed for query: '{}' - Found {} users and {} trips",
                q,
                results.users().size(),
                results.trips().size());

        return ResponseEntity.ok(results);
    }
}
