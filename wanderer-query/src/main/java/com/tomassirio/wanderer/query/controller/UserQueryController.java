package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import com.tomassirio.wanderer.query.dto.UserAdminResponse;
import com.tomassirio.wanderer.query.dto.UserRelationshipResponse;
import com.tomassirio.wanderer.query.dto.UserResponse;
import com.tomassirio.wanderer.query.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user query operations. Handles user retrieval requests.
 *
 * @since 0.1.8
 */
@RestController
@RequestMapping(value = ApiConstants.USERS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Queries", description = "Endpoints for retrieving user information")
public class UserQueryController {

    private final UserQueryService userQueryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get all users with statistics (Admin only)",
            description =
                    "Retrieves all users with their statistics (friends, followers, trips count). "
                            + "Use query parameters: page, size, sort (e.g., sort=username,asc)")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - valid JWT required")
    @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required")
    public ResponseEntity<Page<UserAdminResponse>> getAllUsers(
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(size = 20, sort = "username")
                    Pageable pageable) {
        log.info(
                "Admin retrieving all users, page: {}, size: {}",
                pageable.getPageNumber(),
                pageable.getPageSize());
        Page<UserAdminResponse> users = userQueryService.getAllUsersWithStats(pageable);
        log.info(
                "Successfully retrieved {} users (page {} of {})",
                users.getNumberOfElements(),
                users.getNumber() + 1,
                users.getTotalPages());
        return ResponseEntity.ok(users);
    }

    @GetMapping(ApiConstants.USER_BY_ID_ENDPOINT)
    @Operation(summary = "Get user by ID", description = "Retrieves a specific user by their ID")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        log.info("Retrieving user by ID: {}", id);
        UserResponse user = userQueryService.getUserById(id);
        log.info("Successfully retrieved user with ID: {}", id);
        return ResponseEntity.ok(user);
    }

    @GetMapping(ApiConstants.USERNAME_ENDPOINT)
    @Operation(summary = "Get user by username", description = "Retrieves a user by their username")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        log.info("Retrieving user by username");
        UserResponse user = userQueryService.getUserByUsername(username);
        log.info("Successfully retrieved user by username");
        return ResponseEntity.ok(user);
    }

    @GetMapping(ApiConstants.ME_SUFFIX)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get current authenticated user's profile",
            description = "Retrieves the profile of the currently authenticated user")
    public ResponseEntity<UserResponse> getMyUser(
            @Parameter(hidden = true) @CurrentUserId UUID userId) {
        log.info("Retrieving current user profile for userId: {}", userId);
        UserResponse user = userQueryService.getUserById(userId);
        log.info("Successfully retrieved current user profile");
        return ResponseEntity.ok(user);
    }
    
    @GetMapping(ApiConstants.ME_SUFFIX + "/discover")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get discoverable users",
            description = "Retrieves friends of friends and people followed by friends for user discovery")
    @ApiResponse(responseCode = "200", description = "Discoverable users retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - valid JWT required")
    public ResponseEntity<Page<UserResponse>> getDiscoverableUsers(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving discoverable users for userId: {}, page: {}, size: {}", userId, page, size);
        Page<UserResponse> users = userQueryService.getDiscoverableUsers(userId, page, size);
        log.info("Successfully retrieved {} discoverable users (page {} of {})", 
                users.getContent().size(), users.getNumber() + 1, users.getTotalPages());
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/{targetUserId}/associated")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get users associated with target user",
            description = "Retrieves users associated with the target user (friends, following, followers) "
                    + "with relationship status from the current user's perspective")
    @ApiResponse(responseCode = "200", description = "Associated users retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - valid JWT required")
    public ResponseEntity<Page<UserRelationshipResponse>> getAssociatedUsers(
            @Parameter(hidden = true) @CurrentUserId UUID currentUserId,
            @PathVariable UUID targetUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving associated users for target user {} from current user {}, page: {}, size: {}", 
                targetUserId, currentUserId, page, size);
        Page<UserRelationshipResponse> users = userQueryService.getAssociatedUsers(currentUserId, targetUserId, page, size);
        log.info("Successfully retrieved {} associated users (page {} of {})", 
                users.getContent().size(), users.getNumber() + 1, users.getTotalPages());
        return ResponseEntity.ok(users);
    }
}
