package com.tomassirio.wanderer.query.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.commons.dto.FriendshipResponse;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.security.CurrentUserIdArgumentResolver;
import com.tomassirio.wanderer.commons.security.JwtUtils;
import com.tomassirio.wanderer.query.service.FriendshipQueryService;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FriendshipQueryControllerTest {

    private static final String MY_FRIENDS_URL = "/api/1/users/me/friends";

    private MockMvc mockMvc;

    @Mock private FriendshipQueryService friendshipQueryService;

    @Mock private JwtUtils jwtUtils;

    @InjectMocks private FriendshipQueryController friendshipQueryController;

    private UUID userId;
    private UUID friendId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        friendId = UUID.randomUUID();
        token = "Bearer test-token";

        mockMvc =
                MockMvcBuilders.standaloneSetup(friendshipQueryController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(jwtUtils))
                        .build();
    }

    // ============ GET MY FRIENDS TESTS ============

    @Test
    void getMyFriends_Success() throws Exception {
        when(jwtUtils.getUserIdFromAuthorizationHeader(token)).thenReturn(userId);
        FriendshipResponse response = new FriendshipResponse(userId, friendId);

        Page<FriendshipResponse> page = new PageImpl<>(List.of(response));
        when(friendshipQueryService.getFriends(eq(userId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(MY_FRIENDS_URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].friendId").value(friendId.toString()));

        verify(friendshipQueryService).getFriends(eq(userId), any(Pageable.class));
    }

    @Test
    void getMyFriends_EmptyList() throws Exception {
        when(jwtUtils.getUserIdFromAuthorizationHeader(token)).thenReturn(userId);
        Page<FriendshipResponse> page = new PageImpl<>(List.of());
        when(friendshipQueryService.getFriends(eq(userId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(MY_FRIENDS_URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(friendshipQueryService).getFriends(eq(userId), any(Pageable.class));
    }

    @Test
    void getFriendsByUserId_Success() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID targetFriendId = UUID.randomUUID();
        FriendshipResponse response = new FriendshipResponse(targetUserId, targetFriendId);

        Page<FriendshipResponse> page = new PageImpl<>(List.of(response));
        when(friendshipQueryService.getFriends(eq(targetUserId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/1/users/{userId}/friends", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[0].friendId").value(targetFriendId.toString()));

        verify(friendshipQueryService).getFriends(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFriendsByUserId_EmptyList() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        Page<FriendshipResponse> page = new PageImpl<>(List.of());
        when(friendshipQueryService.getFriends(eq(targetUserId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/1/users/{userId}/friends", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(friendshipQueryService).getFriends(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFriendsByUserId_MultipleFriends() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID friendId1 = UUID.randomUUID();
        UUID friendId2 = UUID.randomUUID();
        FriendshipResponse response1 = new FriendshipResponse(targetUserId, friendId1);
        FriendshipResponse response2 = new FriendshipResponse(targetUserId, friendId2);

        Page<FriendshipResponse> page = new PageImpl<>(List.of(response1, response2));
        when(friendshipQueryService.getFriends(eq(targetUserId), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/1/users/{userId}/friends", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[0].friendId").value(friendId1.toString()))
                .andExpect(jsonPath("$.content[1].userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[1].friendId").value(friendId2.toString()));

        verify(friendshipQueryService).getFriends(eq(targetUserId), any(Pageable.class));
    }

    // ============ PAGINATED MY FRIENDS TESTS ============

    @Test
    void getMyFriends_paginated_Success() throws Exception {
        when(jwtUtils.getUserIdFromAuthorizationHeader(token)).thenReturn(userId);
        FriendshipResponse response1 = new FriendshipResponse(userId, friendId);
        UUID friendId2 = UUID.randomUUID();
        FriendshipResponse response2 = new FriendshipResponse(userId, friendId2);

        Page<FriendshipResponse> page = new PageImpl<>(List.of(response1, response2));
        when(friendshipQueryService.getFriends(eq(userId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(MY_FRIENDS_URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].friendId").value(friendId.toString()))
                .andExpect(jsonPath("$.content[1].userId").value(userId.toString()))
                .andExpect(jsonPath("$.content[1].friendId").value(friendId2.toString()))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(friendshipQueryService).getFriends(eq(userId), any(Pageable.class));
    }

    @Test
    void getMyFriends_paginated_EmptyPage() throws Exception {
        when(jwtUtils.getUserIdFromAuthorizationHeader(token)).thenReturn(userId);
        Page<FriendshipResponse> page = new PageImpl<>(List.of());
        when(friendshipQueryService.getFriends(eq(userId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(MY_FRIENDS_URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(friendshipQueryService).getFriends(eq(userId), any(Pageable.class));
    }

    @Test
    void getMyFriends_paginated_withPaginationParams_shouldPassPageableToService() throws Exception {
        when(jwtUtils.getUserIdFromAuthorizationHeader(token)).thenReturn(userId);
        Page<FriendshipResponse> page = new PageImpl<>(List.of());
        when(friendshipQueryService.getFriends(eq(userId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(MY_FRIENDS_URL)
                        .header("Authorization", token)
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk());

        verify(friendshipQueryService).getFriends(eq(userId), any(Pageable.class));
    }

    // ============ PAGINATED FRIENDS BY USER ID TESTS ============

    @Test
    void getFriendsByUserId_paginated_Success() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID targetFriendId1 = UUID.randomUUID();
        UUID targetFriendId2 = UUID.randomUUID();
        FriendshipResponse response1 = new FriendshipResponse(targetUserId, targetFriendId1);
        FriendshipResponse response2 = new FriendshipResponse(targetUserId, targetFriendId2);

        Page<FriendshipResponse> page = new PageImpl<>(List.of(response1, response2));
        when(friendshipQueryService.getFriends(eq(targetUserId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/1/users/{userId}/friends", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[0].friendId").value(targetFriendId1.toString()))
                .andExpect(jsonPath("$.content[1].userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[1].friendId").value(targetFriendId2.toString()))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(friendshipQueryService).getFriends(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFriendsByUserId_paginated_EmptyPage() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        Page<FriendshipResponse> page = new PageImpl<>(List.of());
        when(friendshipQueryService.getFriends(eq(targetUserId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/1/users/{userId}/friends", targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(friendshipQueryService).getFriends(eq(targetUserId), any(Pageable.class));
    }

    @Test
    void getFriendsByUserId_paginated_withPaginationParams_shouldPassPageableToService() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        Page<FriendshipResponse> page = new PageImpl<>(List.of());
        when(friendshipQueryService.getFriends(eq(targetUserId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/1/users/{userId}/friends", targetUserId)
                        .param("page", "0")
                        .param("size", "25")
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk());

        verify(friendshipQueryService).getFriends(eq(targetUserId), any(Pageable.class));
    }
}
