package com.tomassirio.wanderer.query.dto;

import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import java.util.List;

/**
 * Response DTO for unified search results containing both users and trips.
 *
 * @since 1.2.0
 */
public record SearchResultsResponse(
        List<UserSearchResult> users,
        List<TripSummaryDTO> trips
) {}
