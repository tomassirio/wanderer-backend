package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.query.dto.SearchResultsResponse;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for unified search operations. Provides methods to search for users and trips.
 *
 * @since 1.2.0
 */
public interface SearchService {

    /**
     * Performs a unified search for users and trips based on the search term. Users are searched by
     * username and display name. Trips are searched by name and owner username (partial or full
     * match), following the same criteria as the /public endpoint (ongoing public trips with
     * promoted status). Each category is independently paginated.
     *
     * @param searchTerm the term to search for
     * @param userPageable pagination parameters for users
     * @param tripPageable pagination parameters for trips
     * @return search results containing paginated matching users and trips
     */
    SearchResultsResponse search(String searchTerm, Pageable userPageable, Pageable tripPageable);
}
