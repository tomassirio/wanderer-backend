package com.tomassirio.wanderer.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.query.dto.UserResponse;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.impl.UserQueryServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserQueryServiceImpl userQueryService;

    @Test
    void getUserById_whenUserExists_shouldReturnUserResponse() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = createUser(userId, "johndoe");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        UserResponse result = userQueryService.getUserById(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo("johndoe");

        verify(userRepository).findById(userId);
    }

    @Test
    void getUserById_whenUserDoesNotExist_shouldThrowEntityNotFoundException() {
        // Given
        UUID nonExistentUserId = UUID.randomUUID();
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userQueryService.getUserById(nonExistentUserId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository).findById(nonExistentUserId);
    }

    @Test
    void getUserById_whenUserIdIsNull_shouldThrowResponseStatusException() {
        // When & Then
        assertThatThrownBy(() -> userQueryService.getUserById(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing or invalid authenticated user id")
                .matches(
                        e ->
                                ((ResponseStatusException) e).getStatusCode()
                                        == HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUserById_shouldMapUserFieldsCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = createUser(userId, "testuser123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        UserResponse result = userQueryService.getUserById(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo("testuser123");

        verify(userRepository).findById(userId);
    }

    @Test
    void getUserByUsername_whenUserExists_shouldReturnUserResponse() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "alice";
        User user = createUser(userId, username);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // When
        UserResponse result = userQueryService.getUserByUsername(username);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo(username);

        verify(userRepository).findByUsername(username);
    }

    @Test
    void getUserByUsername_whenMixedCaseInput_shouldNormalizeToLowercase() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = createUser(userId, "alice");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        // When
        UserResponse result = userQueryService.getUserByUsername("Alice");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo("alice");

        verify(userRepository).findByUsername("alice");
    }

    @Test
    void getUserByUsername_whenUserDoesNotExist_shouldThrowEntityNotFoundException() {
        // Given
        String nonExistentUsername = "unknownuser";
        when(userRepository.findByUsername(nonExistentUsername)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userQueryService.getUserByUsername(nonExistentUsername))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository).findByUsername(nonExistentUsername);
    }

    @Test
    void getUserByUsername_withDifferentUsernames_shouldReturnCorrectUsers() {
        // Given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        User user1 = createUser(userId1, "user1");
        User user2 = createUser(userId2, "user2");

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user1));
        when(userRepository.findByUsername("user2")).thenReturn(Optional.of(user2));

        // When
        UserResponse result1 = userQueryService.getUserByUsername("user1");
        UserResponse result2 = userQueryService.getUserByUsername("user2");

        // Then
        assertThat(result1.id()).isEqualTo(userId1);
        assertThat(result1.username()).isEqualTo("user1");
        assertThat(result2.id()).isEqualTo(userId2);
        assertThat(result2.username()).isEqualTo("user2");

        verify(userRepository).findByUsername("user1");
        verify(userRepository).findByUsername("user2");
    }

    @Test
    void getUserByUsername_shouldMapUserFieldsCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "detaileduser";
        User user = createUser(userId, username);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // When
        UserResponse result = userQueryService.getUserByUsername(username);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo(username);

        verify(userRepository).findByUsername(username);
    }

    private User createUser(UUID userId, String username) {
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        return user;
    }
}
