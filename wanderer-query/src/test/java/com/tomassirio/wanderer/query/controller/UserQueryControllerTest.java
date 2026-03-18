package com.tomassirio.wanderer.query.controller;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import com.tomassirio.wanderer.query.dto.UserAdminResponse;
import com.tomassirio.wanderer.query.dto.UserResponse;
import com.tomassirio.wanderer.query.service.UserQueryService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class UserQueryControllerTest {

    private static final String USERS_BASE_URL = "/api/1/users";
    private static final String USERS_ME_URL = USERS_BASE_URL + "/me";

    private MockMvc mockMvc;

    @Mock private UserQueryService userQueryService;

    @InjectMocks private UserQueryController userQueryController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        userQueryController, new GlobalExceptionHandler());
    }

    // --- getAllUsers (admin, paginated) ---

    @Test
    void getAllUsers_whenUsersExist_shouldReturnPageOfUsers() throws Exception {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UserAdminResponse user1 =
                new UserAdminResponse(id1, "alice", null, 5, 10, 3, Instant.now());
        UserAdminResponse user2 = new UserAdminResponse(id2, "bob", null, 2, 4, 1, Instant.now());

        Page<UserAdminResponse> page = new PageImpl<>(List.of(user1, user2));
        when(userQueryService.getAllUsersWithStats(any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(USERS_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(id1.toString()))
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.content[0].friendsCount").value(5))
                .andExpect(jsonPath("$.content[0].followersCount").value(10))
                .andExpect(jsonPath("$.content[0].tripsCount").value(3))
                .andExpect(jsonPath("$.content[1].id").value(id2.toString()))
                .andExpect(jsonPath("$.content[1].username").value("bob"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getAllUsers_whenNoUsersExist_shouldReturnEmptyPage() throws Exception {
        // Given
        Page<UserAdminResponse> page = new PageImpl<>(List.of());
        when(userQueryService.getAllUsersWithStats(any(Pageable.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get(USERS_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getAllUsers_withPaginationParams_shouldPassPageableToService() throws Exception {
        // Given
        Page<UserAdminResponse> page = new PageImpl<>(List.of());
        when(userQueryService.getAllUsersWithStats(any(Pageable.class))).thenReturn(page);

        // When
        mockMvc.perform(get(USERS_BASE_URL).param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        // Then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userQueryService).getAllUsersWithStats(pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assert captured.getPageNumber() == 2;
        assert captured.getPageSize() == 5;
    }

    @Test
    void getAllUsers_withSortParam_shouldPassSortToService() throws Exception {
        // Given
        Page<UserAdminResponse> page = new PageImpl<>(List.of());
        when(userQueryService.getAllUsersWithStats(any(Pageable.class))).thenReturn(page);

        // When
        mockMvc.perform(get(USERS_BASE_URL).param("sort", "username,desc"))
                .andExpect(status().isOk());

        // Then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userQueryService).getAllUsersWithStats(pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assert captured.getSort().getOrderFor("username") != null;
        assert captured.getSort().getOrderFor("username").isDescending();
    }

    @Test
    void getAllUsers_withDefaultParams_shouldUseDefaults() throws Exception {
        // Given
        Page<UserAdminResponse> page = new PageImpl<>(List.of());
        when(userQueryService.getAllUsersWithStats(any(Pageable.class))).thenReturn(page);

        // When
        mockMvc.perform(get(USERS_BASE_URL)).andExpect(status().isOk());

        // Then - default is size=20, sort=username
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userQueryService).getAllUsersWithStats(pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assert captured.getPageNumber() == 0;
        assert captured.getPageSize() == 20;
        assert captured.getSort().getOrderFor("username") != null;
    }

    // --- existing tests ---

    @Test
    void getUser_whenUserExists_shouldReturnUser() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse resp = new UserResponse(id, "johndoe", null);

        when(userQueryService.getUserById(id)).thenReturn(resp);

        mockMvc.perform(get(USERS_BASE_URL + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value("johndoe"));
    }

    @Test
    void getUser_whenUserDoesNotExist_shouldReturnNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(userQueryService.getUserById(id))
                .thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(get(USERS_BASE_URL + "/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void getUserByUsername_whenUserExists_shouldReturnUser() throws Exception {
        String username = "alice";
        UserResponse resp = new UserResponse(UUID.randomUUID(), username, null);

        when(userQueryService.getUserByUsername(username)).thenReturn(resp);

        mockMvc.perform(get(USERS_BASE_URL + "/username/{username}", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void getMyUser_whenUserExists_shouldReturnUser() throws Exception {
        UserResponse resp = new UserResponse(USER_ID, "currentuser", null);

        when(userQueryService.getUserById(USER_ID)).thenReturn(resp);

        mockMvc.perform(get(USERS_ME_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("currentuser"));
    }

    @Test
    void getMyUser_whenUserDoesNotExist_shouldReturnNotFound() throws Exception {
        when(userQueryService.getUserById(USER_ID))
                .thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(get(USERS_ME_URL)).andExpect(status().isNotFound());
    }
}
