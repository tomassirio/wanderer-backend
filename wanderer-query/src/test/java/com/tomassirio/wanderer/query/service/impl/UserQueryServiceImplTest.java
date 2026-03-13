package com.tomassirio.wanderer.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserDetails;
import com.tomassirio.wanderer.query.dto.UserAdminResponse;
import com.tomassirio.wanderer.query.dto.UserResponse;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserFollowRepository userFollowRepository;
    @Mock private TripRepository tripRepository;

    @InjectMocks private UserQueryServiceImpl userQueryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(UUID.randomUUID()).username("testuser").build();
    }

    @Test
    void getUserById_whenUserExists_shouldReturnUserResponse() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        UserResponse result = userQueryService.getUserById(testUser.getId());

        assertEquals(testUser.getId(), result.id());
        assertEquals(testUser.getUsername(), result.username());
    }

    @Test
    void getUserById_whenUserDoesNotExist_shouldThrowEntityNotFoundException() {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class, () -> userQueryService.getUserById(nonExistentId));
    }

    @Test
    void getUserByUsername_whenUserExists_shouldReturnUserResponse() {
        when(userRepository.findByUsername(testUser.getUsername()))
                .thenReturn(Optional.of(testUser));

        UserResponse result = userQueryService.getUserByUsername(testUser.getUsername());

        assertEquals(testUser.getId(), result.id());
        assertEquals(testUser.getUsername(), result.username());
    }

    @Test
    void getUserByUsername_whenMixedCaseInput_shouldNormalizeToLowercase() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserResponse result = userQueryService.getUserByUsername("TestUser");

        assertEquals(testUser.getId(), result.id());
        assertEquals(testUser.getUsername(), result.username());
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void getUserByUsername_whenUserDoesNotExist_shouldThrowEntityNotFoundException() {
        String nonExistentUsername = "nonexistent";
        when(userRepository.findByUsername(nonExistentUsername)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> userQueryService.getUserByUsername(nonExistentUsername));
    }

    @Test
    void getAllUsers_shouldReturnPagedUserResponses() {
        // Given
        User user1 = User.builder().id(UUID.randomUUID()).username("alice").build();
        User user2 = User.builder().id(UUID.randomUUID()).username("bob").build();
        List<User> users = List.of(user1, user2);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("username"));
        Page<User> userPage = new PageImpl<>(users, pageable, users.size());

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // When
        Page<UserResponse> result = userQueryService.getAllUsers(pageable);

        // Then
        assertEquals(2, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        assertEquals(2, result.getContent().size());
        assertEquals("alice", result.getContent().get(0).username());
        assertEquals("bob", result.getContent().get(1).username());
    }

    @Test
    void getAllUsers_withEmptyResult_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findAll(pageable)).thenReturn(emptyPage);

        // When
        Page<UserResponse> result = userQueryService.getAllUsers(pageable);

        // Then
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());
    }

    @Test
    void getAllUsers_withPagination_shouldReturnCorrectPage() {
        // Given
        User user1 =
                User.builder()
                        .id(UUID.randomUUID())
                        .username("charlie")
                        .createdAt(Instant.now())
                        .build();
        Pageable pageable = PageRequest.of(1, 2, Sort.by("username")); // Second page, 2 per page
        Page<User> userPage = new PageImpl<>(List.of(user1), pageable, 5); // 5 total, showing 1

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // When
        Page<UserResponse> result = userQueryService.getAllUsers(pageable);

        // Then
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages()); // 5 items / 2 per page = 3 pages
        assertEquals(1, result.getContent().size());
        assertEquals("charlie", result.getContent().get(0).username());
    }

    @Test
    void getAllUsersWithStats_shouldReturnUserAdminResponsesWithCounts() {
        // Given
        User user1 = User.builder().id(UUID.randomUUID()).username("alice").build();
        User user2 = User.builder().id(UUID.randomUUID()).username("bob").build();
        List<User> users = List.of(user1, user2);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("username"));
        Page<User> userPage = new PageImpl<>(users, pageable, users.size());

        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(friendshipRepository.countByUserId(user1.getId())).thenReturn(5L);
        when(userFollowRepository.countByFollowedId(user1.getId())).thenReturn(10L);
        when(tripRepository.countByUserId(user1.getId())).thenReturn(3L);
        when(friendshipRepository.countByUserId(user2.getId())).thenReturn(8L);
        when(userFollowRepository.countByFollowedId(user2.getId())).thenReturn(15L);
        when(tripRepository.countByUserId(user2.getId())).thenReturn(7L);

        // When
        Page<UserAdminResponse> result = userQueryService.getAllUsersWithStats(pageable);

        // Then
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        UserAdminResponse aliceResponse = result.getContent().get(0);
        assertEquals("alice", aliceResponse.username());
        assertEquals(5L, aliceResponse.friendsCount());
        assertEquals(10L, aliceResponse.followersCount());
        assertEquals(3L, aliceResponse.tripsCount());

        UserAdminResponse bobResponse = result.getContent().get(1);
        assertEquals("bob", bobResponse.username());
        assertEquals(8L, bobResponse.friendsCount());
        assertEquals(15L, bobResponse.followersCount());
        assertEquals(7L, bobResponse.tripsCount());
    }

    @Test
    void getAllUsersWithStats_withEmptyResult_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findAll(pageable)).thenReturn(emptyPage);

        // When
        Page<UserAdminResponse> result = userQueryService.getAllUsersWithStats(pageable);

        // Then
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());
    }

    @Test
    void getAllUsersWithStats_withZeroCounts_shouldReturnZeroValues() {
        // Given
        User user = User.builder().id(UUID.randomUUID()).username("newuser").build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> userPage = new PageImpl<>(List.of(user), pageable, 1);

        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(friendshipRepository.countByUserId(user.getId())).thenReturn(0L);
        when(userFollowRepository.countByFollowedId(user.getId())).thenReturn(0L);
        when(tripRepository.countByUserId(user.getId())).thenReturn(0L);

        // When
        Page<UserAdminResponse> result = userQueryService.getAllUsersWithStats(pageable);

        // Then
        assertEquals(1, result.getContent().size());
        UserAdminResponse response = result.getContent().get(0);
        assertEquals("newuser", response.username());
        assertEquals(0L, response.friendsCount());
        assertEquals(0L, response.followersCount());
        assertEquals(0L, response.tripsCount());
    }

    @Test
    void getUserById_whenUserHasDetails_shouldReturnUserDetailsInResponse() {
        // Given
        UserDetails details =
                UserDetails.builder()
                        .displayName("John Doe")
                        .bio("Walking the Camino")
                        .avatarUrl("https://example.com/avatar.png")
                        .build();
        User user =
                User.builder()
                        .id(UUID.randomUUID())
                        .username("johndoe")
                        .userDetails(details)
                        .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        // When
        UserResponse result = userQueryService.getUserById(user.getId());

        // Then
        assertNotNull(result.userDetails());
        assertEquals("John Doe", result.userDetails().displayName());
        assertEquals("Walking the Camino", result.userDetails().bio());
        assertEquals("https://example.com/avatar.png", result.userDetails().avatarUrl());
    }

    @Test
    void getUserById_whenUserHasNoDetails_shouldReturnEmptyUserDetailsInResponse() {
        // Given
        User user = User.builder().id(UUID.randomUUID()).username("newuser").build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        // When
        UserResponse result = userQueryService.getUserById(user.getId());

        // Then
        assertNotNull(result.userDetails());
        assertNull(result.userDetails().displayName());
        assertNull(result.userDetails().bio());
        assertNull(result.userDetails().avatarUrl());
    }
}
