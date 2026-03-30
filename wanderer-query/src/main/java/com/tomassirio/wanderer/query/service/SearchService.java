package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.query.dto.SearchResultsResponse;

/**
 * Service interface for unified search operations. Provides methods to search for users and trips.
 *
 * @since 1.2.0
 */
public interface SearchService {

    /**
     * Performs a unified search for users and trips based on the search term.
     * Users are searched by username and display name.
     * Trips are searched by name and follow the same criteria as the /public endpoint
     * (ongoing public trips with promoted status).
     *
     * @param searchTerm the term to search for
     * @param limit maximum number of results to return for each category (users/trips)
     * @return search results containing matching users and trips
     */
    SearchResultsResponse search(String searchTerm, int limit);
}
