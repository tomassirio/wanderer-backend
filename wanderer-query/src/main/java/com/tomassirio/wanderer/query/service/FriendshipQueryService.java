package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.commons.dto.FriendshipResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service responsible for querying friendship information.
 *
 * @author tomassirio
 * @since 0.4.0
 */
public interface FriendshipQueryService {

    /**
     * Get all friends for a user.
     *
     * @param userId the ID of the user
     * @return list of friendships
     */
    List<FriendshipResponse> getFriends(UUID userId);

    /**
     * Get all friends for a user with pagination.
     *
     * @param userId the ID of the user
     * @param pageable pagination and sorting parameters
     * @return page of friendships
     */
    Page<FriendshipResponse> getFriends(UUID userId, Pageable pageable);
}
