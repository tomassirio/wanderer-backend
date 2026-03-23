package com.tomassirio.wanderer.command.controller;

import com.tomassirio.wanderer.command.controller.request.UserCreationRequest;
import com.tomassirio.wanderer.command.controller.request.UserDetailsRequest;
import com.tomassirio.wanderer.command.service.UserService;
import com.tomassirio.wanderer.command.validator.ImageFileValidator;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for user command operations. Handles user creation and self-deletion requests.
 *
 * @since 0.1.8
 */
@RestController
@RequestMapping(value = ApiConstants.USERS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "Endpoints for managing users")
public class UserController {

    private final UserService userService;
    private final ImageFileValidator imageFileValidator;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new user",
            description =
                    "Registers a new user in the system. Returns 202 Accepted with the user ID as the operation completes asynchronously.")
    public ResponseEntity<UUID> createUser(@Valid @RequestBody UserCreationRequest request) {
        log.info("Received request to create new user");
        UUID userId = userService.createUser(request);
        log.info("Accepted user creation request with ID: {}", userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(userId);
    }

    @PatchMapping(value = ApiConstants.ME_SUFFIX, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Update current user's details",
            description =
                    "Updates the authenticated user's profile details (display name, bio). "
                            + "Only provided (non-null) fields are updated. "
                            + "Returns 202 Accepted with the user ID as the operation completes asynchronously.")
    @ApiResponse(responseCode = "202", description = "User details update accepted")
    @ApiResponse(responseCode = "401", description = "Unauthorized - valid JWT required")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UUID> updateMyDetails(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody UserDetailsRequest request) {
        log.info("User {} updating their details", userId);
        UUID updatedUserId = userService.updateUserDetails(userId, request);
        log.info("Accepted user details update for user: {}", updatedUserId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(updatedUserId);
    }

    @PostMapping(
            value = ApiConstants.ME_SUFFIX + "/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Upload profile avatar",
            description =
                    "Uploads a new profile picture for the authenticated user. "
                            + "Accepts JPEG, PNG, or WebP images. "
                            + "Maximum file size: 5MB. "
                            + "Image will be resized to 512x512. "
                            + "Returns 202 Accepted with the user ID as the operation completes asynchronously.")
    @ApiResponse(responseCode = "202", description = "Avatar upload accepted")
    @ApiResponse(responseCode = "400", description = "Invalid file format or size")
    @ApiResponse(responseCode = "401", description = "Unauthorized - valid JWT required")
    public ResponseEntity<UUID> uploadAvatar(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Parameter(description = "Profile picture file (JPEG, PNG, WebP, max 5MB)")
                    MultipartFile file) {

        imageFileValidator.validate(file);

        log.info("User {} uploading avatar", userId);
        UUID updatedUserId = userService.updateAvatar(userId, file);
        log.info("Accepted avatar upload for user: {}", updatedUserId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(updatedUserId);
    }

    @DeleteMapping(value = ApiConstants.ME_SUFFIX + "/avatar")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Delete profile avatar",
            description =
                    "Removes the authenticated user's profile picture. "
                            + "Returns 202 Accepted as the operation completes asynchronously.")
    @ApiResponse(responseCode = "202", description = "Avatar deletion accepted")
    @ApiResponse(responseCode = "401", description = "Unauthorized - valid JWT required")
    public ResponseEntity<UUID> deleteAvatar(@Parameter(hidden = true) @CurrentUserId UUID userId) {
        log.info("User {} deleting avatar", userId);
        UUID updatedUserId = userService.deleteAvatar(userId);
        log.info("Accepted avatar deletion for user: {}", updatedUserId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(updatedUserId);
    }

    @DeleteMapping(ApiConstants.ME_SUFFIX)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Delete own account",
            description =
                    "Deletes the currently authenticated user's account and all associated data "
                            + "(trips, comments, friendships, follows), then removes credentials from the auth service.")
    @ApiResponse(responseCode = "202", description = "Account deletion accepted")
    @ApiResponse(responseCode = "401", description = "Unauthorized - valid JWT required")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> deleteMe(@Parameter(hidden = true) @CurrentUserId UUID userId) {
        log.info("User {} requesting self-deletion", userId);
        userService.deleteUser(userId);
        log.info("Self-deletion completed for user: {}", userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Internal endpoint for deleting only a user's local data. Used by the auth service as a
     * compensation step during registration rollback. Not intended for frontend use.
     *
     * @param id the ID of the user to delete
     * @return 202 Accepted
     */
    @Hidden
    @DeleteMapping(ApiConstants.USER_BY_ID_ENDPOINT)
    public ResponseEntity<Void> deleteUserData(@PathVariable UUID id) {
        log.info("Internal request to delete user data: {}", id);
        userService.deleteUserData(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
