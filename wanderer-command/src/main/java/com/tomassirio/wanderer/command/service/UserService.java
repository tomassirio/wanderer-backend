package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.command.controller.request.UserCreationRequest;
import com.tomassirio.wanderer.command.controller.request.UserDetailsRequest;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service responsible for handling user write operations (command side).
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Create users (ensuring username and email uniqueness).
 *   <li>Any command-side user mutations belong here.
 * </ul>
 *
 * <p>Contracts:
 *
 * <ul>
 *   <li>Input: {@link UserCreationRequest} containing username and email (validated by the
 *       controller).
 *   <li>Output: UUID of the created user.
 *   <li>Error modes: throws {@link IllegalArgumentException} when username or email are already in
 *       use.
 * </ul>
 *
 * * @author tomassirio * @since 0.1.8
 */
public interface UserService {

    /**
     * Create a new user from the provided request.
     *
     * @param request validated creation request containing username and email
     * @return the UUID of the created user
     * @throws IllegalArgumentException if username or email are already in use
     */
    UUID createUser(UserCreationRequest request);

    /**
     * Delete a user, all associated data, and their credentials from the auth service. Used by both
     * self-deletion ({@code /me}) and admin deletion.
     *
     * @param userId the UUID of the user to delete
     * @throws jakarta.persistence.EntityNotFoundException if the user does not exist
     */
    void deleteUser(UUID userId);

    /**
     * Delete only the user's local data (trips, comments, friendships, follows, etc.) without
     * touching auth credentials. Used as a compensation step during registration rollback.
     *
     * @param userId the UUID of the user to delete
     * @throws jakarta.persistence.EntityNotFoundException if the user does not exist
     */
    void deleteUserData(UUID userId);

    /**
     * Update the user details (display name, bio) for the given user.
     *
     * @param userId the UUID of the user to update
     * @param request the user details to set
     * @return the UUID of the user being updated
     * @throws jakarta.persistence.EntityNotFoundException if the user does not exist
     */
    UUID updateUserDetails(UUID userId, UserDetailsRequest request);

    /**
     * Upload and set a new profile avatar for the given user.
     *
     * @param userId the UUID of the user
     * @param file the avatar image file (JPEG, PNG, WebP)
     * @return the UUID of the user being updated
     * @throws IllegalArgumentException if file is invalid or too large
     * @throws jakarta.persistence.EntityNotFoundException if the user does not exist
     */
    UUID updateAvatar(UUID userId, MultipartFile file);

    /**
     * Delete the profile avatar for the given user.
     *
     * @param userId the UUID of the user
     * @return the UUID of the user being updated
     * @throws jakarta.persistence.EntityNotFoundException if the user does not exist
     */
    UUID deleteAvatar(UUID userId);
}
