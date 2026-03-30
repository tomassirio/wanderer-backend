package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.FriendshipResponse;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import com.tomassirio.wanderer.query.service.FriendshipQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for querying friendship information.
 *
 * @author tomassirio
 * @since 0.4.0
 */
@RestController
@RequestMapping(value = ApiConstants.USERS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Friends Query", description = "Endpoints for querying friends")
public class FriendshipQueryController {

    private final FriendshipQueryService friendshipQueryService;

    @GetMapping(ApiConstants.FRIENDS_ME_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get my friends", 
            description = "Get all friends of the current user with pagination. "
                    + "Use query parameters: page, size, sort (e.g., sort=createdAt,desc)")
    public ResponseEntity<Page<FriendshipResponse>> getMyFriends(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info("Received request to get friends for current user {} with pagination", userId);
        Page<FriendshipResponse> friends = friendshipQueryService.getFriends(userId, pageable);
        log.info("Found {} friends for user {} (page {} of {})", 
                friends.getNumberOfElements(), userId, friends.getNumber() + 1, friends.getTotalPages());
        return ResponseEntity.ok(friends);
    }

    @GetMapping(ApiConstants.FRIENDS_BY_USER_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Get user's friends",
            description = "Get all friends of a specific user by user ID with pagination. "
                    + "Use query parameters: page, size, sort (e.g., sort=createdAt,desc)")
    public ResponseEntity<Page<FriendshipResponse>> getFriendsByUserId(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info("Received request to get friends for user {} with pagination", userId);
        Page<FriendshipResponse> friends = friendshipQueryService.getFriends(userId, pageable);
        log.info("Found {} friends for user {} (page {} of {})", 
                friends.getNumberOfElements(), userId, friends.getNumber() + 1, friends.getTotalPages());
        return ResponseEntity.ok(friends);
    }
}
