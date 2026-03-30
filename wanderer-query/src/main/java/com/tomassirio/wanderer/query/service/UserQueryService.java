package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.query.dto.UserAdminResponse;
import com.tomassirio.wanderer.query.dto.UserRelationshipResponse;
import com.tomassirio.wanderer.query.dto.UserResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for user query operations. Provides methods to retrieve user information.
 *
 * @since 0.1.8
 */
public interface UserQueryService {

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param id the user's unique identifier
     * @return the user response containing user details
     * @throws jakarta.persistence.EntityNotFoundException if the user is not found
     */
    UserResponse getUserById(UUID id);

    /**
     * Retrieves a user by their username.
     *
     * @param username the user's username
     * @return the user response containing user details
     * @throws jakarta.persistence.EntityNotFoundException if the user is not found
     */
    UserResponse getUserByUsername(String username);

    /**
     * Retrieves all users with pagination and sorting support.
     *
     * @param pageable pagination and sorting information
     * @return a page of user responses
     */
    Page<UserResponse> getAllUsers(Pageable pageable);

    /**
     * Retrieves all users with statistics for admin view. Includes friends count, followers count,
     * and trips count for each user.
     *
     * @param pageable pagination and sorting information
     * @return a page of user admin responses with statistics
     */
    Page<UserAdminResponse> getAllUsersWithStats(Pageable pageable);

    /**
     * Retrieves discoverable users for the current user. Returns friends of friends first, then
     * people followed by friends.
     *
     * @param currentUserId the ID of the current user
     * @param page the page number
     * @param size the page size
     * @return page of discoverable users
     */
    Page<UserResponse> getDiscoverableUsers(UUID currentUserId, int page, int size);

    /**
     * Retrieves users associated with the target user, showing their relationship status (friend,
     * following, followed) from the perspective of the current user.
     *
     * @param currentUserId the ID of the current user making the request
     * @param targetUserId the ID of the user whose associated users to retrieve
     * @param page the page number
     * @param size the page size
     * @return page of users with relationship status
     */
    Page<UserRelationshipResponse> getAssociatedUsers(
            UUID currentUserId, UUID targetUserId, int page, int size);
}
