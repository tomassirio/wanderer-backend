package com.tomassirio.wanderer.query.controller;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.commons.dto.UserFollowResponse;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import com.tomassirio.wanderer.query.service.UserFollowService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class UserFollowQueryControllerTest {

    private static final String MY_FOLLOWING_URL = "/api/1/users/me/following";
    private static final String MY_FOLLOWERS_URL = "/api/1/users/me/followers";
    private static final String USER_FOLLOWING_URL = "/api/1/users/{userId}/following";
    private static final String USER_FOLLOWERS_URL = "/api/1/users/{userId}/followers";

    private MockMvc mockMvc;

    @Mock private UserFollowService userFollowService;

    @InjectMocks private UserFollowQueryController userFollowQueryController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        userFollowQueryController, new GlobalExceptionHandler());
    }

    // ============ GET MY FOLLOWING TESTS ============

    @Test
    void getMyFollowing_whenUserHasFollowing_shouldReturnFollowingList() throws Exception {
        // Given
        UUID followedUserId1 = UUID.randomUUID();
        UUID followedUserId2 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        UUID followId2 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, USER_ID, followedUserId1, now);
        UserFollowResponse follow2 =
                new UserFollowResponse(followId2, USER_ID, followedUserId2, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1, follow2));
        when(userFollowService.getFollowing(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.content[0].followerId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.content[0].followedId").value(followedUserId1.toString()))
                .andExpect(jsonPath("$.content[1].id").value(followId2.toString()))
                .andExpect(jsonPath("$.content[1].followerId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.content[1].followedId").value(followedUserId2.toString()));

        verify(userFollowService).getFollowing(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowing_whenUserHasNoFollowing_shouldReturnEmptyList() throws Exception {
        // Given
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowing(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(userFollowService).getFollowing(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowing_whenUserFollowsOneUser_shouldReturnSingleFollow() throws Exception {
        // Given
        UUID followedUserId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow = new UserFollowResponse(followId, USER_ID, followedUserId, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow));
        when(userFollowService.getFollowing(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(followId.toString()))
                .andExpect(jsonPath("$.content[0].followerId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.content[0].followedId").value(followedUserId.toString()))
                .andExpect(jsonPath("$.content[0].createdAt").exists());

        verify(userFollowService).getFollowing(eq(USER_ID), any(Pageable.class));
    }

    // ============ GET MY FOLLOWERS TESTS ============

    @Test
    void getMyFollowers_whenUserHasFollowers_shouldReturnFollowersList() throws Exception {
        // Given
        UUID followerUserId1 = UUID.randomUUID();
        UUID followerUserId2 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        UUID followId2 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, followerUserId1, USER_ID, now);
        UserFollowResponse follow2 =
                new UserFollowResponse(followId2, followerUserId2, USER_ID, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1, follow2));
        when(userFollowService.getFollowers(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.content[0].followerId").value(followerUserId1.toString()))
                .andExpect(jsonPath("$.content[0].followedId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.content[1].id").value(followId2.toString()))
                .andExpect(jsonPath("$.content[1].followerId").value(followerUserId2.toString()))
                .andExpect(jsonPath("$.content[1].followedId").value(USER_ID.toString()));

        verify(userFollowService).getFollowers(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowers_whenUserHasNoFollowers_shouldReturnEmptyList() throws Exception {
        // Given
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowers(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(userFollowService).getFollowers(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowers_whenUserHasOneFollower_shouldReturnSingleFollower() throws Exception {
        // Given
        UUID followerUserId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow = new UserFollowResponse(followId, followerUserId, USER_ID, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow));
        when(userFollowService.getFollowers(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(followId.toString()))
                .andExpect(jsonPath("$.content[0].followerId").value(followerUserId.toString()))
                .andExpect(jsonPath("$.content[0].followedId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.content[0].createdAt").exists());

        verify(userFollowService).getFollowers(eq(USER_ID), any(Pageable.class));
    }

    // ============ GET FOLLOWING BY USER ID TESTS ============

    @Test
    void getFollowingByUserId_whenUserHasFollowing_shouldReturnFollowingList() throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();
        UUID followedUserId1 = UUID.randomUUID();
        UUID followedUserId2 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        UUID followId2 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, targetUserId, followedUserId1, now);
        UserFollowResponse follow2 =
                new UserFollowResponse(followId2, targetUserId, followedUserId2, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1, follow2));
        when(userFollowService.getFollowing(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWING_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.content[0].followerId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[0].followedId").value(followedUserId1.toString()))
                .andExpect(jsonPath("$.content[1].id").value(followId2.toString()))
                .andExpect(jsonPath("$.content[1].followerId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[1].followedId").value(followedUserId2.toString()));

        verify(userFollowService).getFollowing(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFollowingByUserId_whenUserHasNoFollowing_shouldReturnEmptyList() throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();

        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowing(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWING_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(userFollowService).getFollowing(eq(targetUserId), any(Pageable.class));
    }

    // ============ GET FOLLOWERS BY USER ID TESTS ============

    @Test
    void getFollowersByUserId_whenUserHasFollowers_shouldReturnFollowersList() throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();
        UUID followerUserId1 = UUID.randomUUID();
        UUID followerUserId2 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        UUID followId2 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, followerUserId1, targetUserId, now);
        UserFollowResponse follow2 =
                new UserFollowResponse(followId2, followerUserId2, targetUserId, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1, follow2));
        when(userFollowService.getFollowers(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWERS_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.content[0].followerId").value(followerUserId1.toString()))
                .andExpect(jsonPath("$.content[0].followedId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(followId2.toString()))
                .andExpect(jsonPath("$.content[1].followerId").value(followerUserId2.toString()))
                .andExpect(jsonPath("$.content[1].followedId").value(targetUserId.toString()));

        verify(userFollowService).getFollowers(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFollowersByUserId_whenUserHasNoFollowers_shouldReturnEmptyList() throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();

        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowers(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWERS_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(userFollowService).getFollowers(eq(targetUserId), any(Pageable.class));
    }

    // ============ ADDITIONAL TESTS ============

    @Test
    void getMyFollowing_shouldReturnFollowsWithCreatedAtTimestamp() throws Exception {
        // Given
        UUID followedUserId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-11-12T10:00:00Z");

        UserFollowResponse follow =
                new UserFollowResponse(followId, USER_ID, followedUserId, createdAt);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow));
        when(userFollowService.getFollowing(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());

        verify(userFollowService).getFollowing(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowers_shouldReturnFollowsWithCreatedAtTimestamp() throws Exception {
        // Given
        UUID followerUserId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-11-12T10:00:00Z");

        UserFollowResponse follow =
                new UserFollowResponse(followId, followerUserId, USER_ID, createdAt);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow));
        when(userFollowService.getFollowers(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());

        verify(userFollowService).getFollowers(eq(USER_ID), any(Pageable.class));
    }

    // ============ PAGINATED FOLLOWING TESTS ============

    @Test
    void getMyFollowing_paginated_whenUserHasFollowing_shouldReturnPageOfFollowing()
            throws Exception {
        // Given
        UUID followedUserId1 = UUID.randomUUID();
        UUID followedUserId2 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        UUID followId2 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, USER_ID, followedUserId1, now);
        UserFollowResponse follow2 =
                new UserFollowResponse(followId2, USER_ID, followedUserId2, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1, follow2));
        when(userFollowService.getFollowing(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.content[1].id").value(followId2.toString()))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(userFollowService).getFollowing(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowing_paginated_whenUserHasNoFollowing_shouldReturnEmptyPage() throws Exception {
        // Given
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowing(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWING_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(userFollowService).getFollowing(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowing_paginated_withPaginationParams_shouldPassPageableToService()
            throws Exception {
        // Given
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowing(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(
                        get(MY_FOLLOWING_URL)
                                .param("page", "1")
                                .param("size", "10")
                                .param("sort", "createdAt,desc"))
                .andExpect(status().isOk());

        verify(userFollowService).getFollowing(eq(USER_ID), any(Pageable.class));
    }

    // ============ PAGINATED FOLLOWERS TESTS ============

    @Test
    void getMyFollowers_paginated_whenUserHasFollowers_shouldReturnPageOfFollowers()
            throws Exception {
        // Given
        UUID followerUserId1 = UUID.randomUUID();
        UUID followerUserId2 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        UUID followId2 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, followerUserId1, USER_ID, now);
        UserFollowResponse follow2 =
                new UserFollowResponse(followId2, followerUserId2, USER_ID, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1, follow2));
        when(userFollowService.getFollowers(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.content[1].id").value(followId2.toString()))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(userFollowService).getFollowers(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowers_paginated_whenUserHasNoFollowers_shouldReturnEmptyPage() throws Exception {
        // Given
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowers(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(MY_FOLLOWERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(userFollowService).getFollowers(eq(USER_ID), any(Pageable.class));
    }

    @Test
    void getMyFollowers_paginated_withPaginationParams_shouldPassPageableToService()
            throws Exception {
        // Given
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowers(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(
                        get(MY_FOLLOWERS_URL)
                                .param("page", "2")
                                .param("size", "15")
                                .param("sort", "createdAt,asc"))
                .andExpect(status().isOk());

        verify(userFollowService).getFollowers(eq(USER_ID), any(Pageable.class));
    }

    // ============ PAGINATED FOLLOWING BY USER ID TESTS ============

    @Test
    void getFollowingByUserId_paginated_whenUserHasFollowing_shouldReturnPageOfFollowing()
            throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();
        UUID followedUserId1 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, targetUserId, followedUserId1, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1));
        when(userFollowService.getFollowing(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWING_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(userFollowService).getFollowing(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFollowingByUserId_paginated_whenUserHasNoFollowing_shouldReturnEmptyPage()
            throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowing(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWING_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(userFollowService).getFollowing(eq(targetUserId), any(Pageable.class));
    }

    // ============ PAGINATED FOLLOWERS BY USER ID TESTS ============

    @Test
    void getFollowersByUserId_paginated_whenUserHasFollowers_shouldReturnPageOfFollowers()
            throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();
        UUID followerUserId1 = UUID.randomUUID();
        UUID followId1 = UUID.randomUUID();
        Instant now = Instant.now();

        UserFollowResponse follow1 =
                new UserFollowResponse(followId1, followerUserId1, targetUserId, now);

        Page<UserFollowResponse> page = new PageImpl<>(List.of(follow1));
        when(userFollowService.getFollowers(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWERS_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(followId1.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(userFollowService).getFollowers(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFollowersByUserId_paginated_whenUserHasNoFollowers_shouldReturnEmptyPage()
            throws Exception {
        // Given
        UUID targetUserId = UUID.randomUUID();
        Page<UserFollowResponse> page = new PageImpl<>(Collections.emptyList());
        when(userFollowService.getFollowers(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get(USER_FOLLOWERS_URL, targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(userFollowService).getFollowers(eq(targetUserId), any(Pageable.class));
    }
}
