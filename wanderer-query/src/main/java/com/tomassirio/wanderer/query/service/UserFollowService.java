package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.commons.dto.UserFollowResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for querying user follows.
 *
 * @author tomassirio
 * @since 0.4.5
 */
public interface UserFollowService {

    /**
     * Get list of users that the current user is following.
     *
     * @param followerId the ID of the user whose following list to retrieve
     * @return list of follows
     */
    List<UserFollowResponse> getFollowing(UUID followerId);

    /**
     * Get list of users that the current user is following with pagination.
     *
     * @param followerId the ID of the user whose following list to retrieve
     * @param pageable pagination and sorting parameters
     * @return page of follows
     */
    Page<UserFollowResponse> getFollowing(UUID followerId, Pageable pageable);

    /**
     * Get list of users that are following the current user.
     *
     * @param followedId the ID of the user whose followers to retrieve
     * @return list of followers
     */
    List<UserFollowResponse> getFollowers(UUID followedId);

    /**
     * Get list of users that are following the current user with pagination.
     *
     * @param followedId the ID of the user whose followers to retrieve
     * @param pageable pagination and sorting parameters
     * @return page of followers
     */
    Page<UserFollowResponse> getFollowers(UUID followedId, Pageable pageable);
}
