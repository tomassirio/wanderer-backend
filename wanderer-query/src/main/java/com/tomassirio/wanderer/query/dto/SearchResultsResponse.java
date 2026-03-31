package com.tomassirio.wanderer.query.dto;

import com.tomassirio.wanderer.commons.dto.TripSummaryDTO;
import org.springframework.data.domain.Page;

/**
 * Response DTO for unified search results containing both users and trips. Each category is
 * independently paginated.
 *
 * @since 1.2.0
 */
public record SearchResultsResponse(
        Page<UserSearchResult> users,
        Page<TripSummaryDTO> trips
) {}
