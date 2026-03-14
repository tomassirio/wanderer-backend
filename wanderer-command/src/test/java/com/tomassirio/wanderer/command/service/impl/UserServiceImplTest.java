package com.tomassirio.wanderer.command.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.client.WandererAuthClient;
import com.tomassirio.wanderer.command.controller.request.UserCreationRequest;
import com.tomassirio.wanderer.command.controller.request.UserDetailsRequest;
import com.tomassirio.wanderer.command.event.UserCreatedEvent;
import com.tomassirio.wanderer.command.event.UserDeletedEvent;
import com.tomassirio.wanderer.command.event.UserDetailsUpdatedEvent;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.commons.domain.User;
import feign.FeignException;
import feign.Request;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private WandererAuthClient wandererAuthClient;

    @InjectMocks private UserServiceImpl userService;

    @Test
    void createUser_whenValid_shouldPublishEventAndReturnUserId() {
        // Given
        UserCreationRequest req = new UserCreationRequest("johndoe", "john@example.com", null);
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.empty());

        // When
        UUID result = userService.createUser(req);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<UserCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(result);
        assertThat(event.getUsername()).isEqualTo("johndoe");
    }

    @Test
    void createUser_whenMixedCaseUsername_shouldStoreLowercaseUsername() {
        // Given
        UserCreationRequest req = new UserCreationRequest("JohnDoe", "john@example.com", "JohnDoe");
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.empty());

        // When
        UUID result = userService.createUser(req);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<UserCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getUsername()).isEqualTo("johndoe");
        assertThat(event.getDisplayName()).isEqualTo("JohnDoe");
        verify(userRepository).findByUsername("johndoe");
    }

    @Test
    void createUser_whenUsernameExists_shouldThrowException() {
        // Given
        UserCreationRequest req = new UserCreationRequest("johndoe", "john@example.com", null);
        User existing = User.builder().id(UUID.randomUUID()).username("johndoe").build();
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(existing));

        // When & Then
        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already in use");

        verify(eventPublisher, never()).publishEvent(any(UserCreatedEvent.class));
    }

    @Test
    void createUser_whenMixedCaseUsernameExists_shouldThrowException() {
        // Given — request with uppercase, existing stored as lowercase
        UserCreationRequest req = new UserCreationRequest("JohnDoe", "john@example.com", "JohnDoe");
        User existing = User.builder().id(UUID.randomUUID()).username("johndoe").build();
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(existing));

        // When & Then
        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already in use");

        verify(eventPublisher, never()).publishEvent(any(UserCreatedEvent.class));
    }

    @Test
    void deleteUser_whenUserExists_shouldPublishEventAndDeleteCredentials() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("johndoe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doNothing().when(wandererAuthClient).deleteCredentials(userId);

        // When
        userService.deleteUser(userId);

        // Then
        ArgumentCaptor<UserDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(userId);
        verify(wandererAuthClient).deleteCredentials(userId);
    }

    @Test
    void deleteUser_whenUserNotFound_shouldThrowException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(eventPublisher, never()).publishEvent(any(UserDeletedEvent.class));
    }

    @Test
    void deleteUser_whenAuthReturns400_shouldStillSucceed() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("johndoe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Request request =
                Request.create(
                        Request.HttpMethod.DELETE,
                        "/api/1/admin/users/" + userId + "/credentials",
                        Collections.emptyMap(),
                        null,
                        null,
                        null);
        doThrow(new FeignException.BadRequest("User not found", request, null, null))
                .when(wandererAuthClient)
                .deleteCredentials(userId);

        // When
        userService.deleteUser(userId);

        // Then
        ArgumentCaptor<UserDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void deleteUser_whenAuthReturns404_shouldStillSucceed() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("johndoe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Request request =
                Request.create(
                        Request.HttpMethod.DELETE,
                        "/api/1/admin/users/" + userId + "/credentials",
                        Collections.emptyMap(),
                        null,
                        null,
                        null);
        doThrow(new FeignException.NotFound("User not found", request, null, null))
                .when(wandererAuthClient)
                .deleteCredentials(userId);

        // When
        userService.deleteUser(userId);

        // Then
        ArgumentCaptor<UserDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void deleteUser_whenAuthReturnsUnexpectedError_shouldThrowException() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("johndoe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Connection refused"))
                .when(wandererAuthClient)
                .deleteCredentials(userId);

        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void deleteUserData_whenUserExists_shouldPublishEventWithoutDeletingCredentials() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("johndoe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        userService.deleteUserData(userId);

        // Then
        ArgumentCaptor<UserDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(userId);
        verify(wandererAuthClient, never()).deleteCredentials(any());
    }

    @Test
    void deleteUserData_whenUserNotFound_shouldThrowException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.deleteUserData(userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(eventPublisher, never()).publishEvent(any(UserDeletedEvent.class));
    }

    @Test
    void updateUserDetails_whenUserExists_shouldPublishEventAndReturnUserId() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("johndoe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserDetailsRequest request =
                new UserDetailsRequest(
                        "John Doe", "Hiking enthusiast", "https://example.com/avatar.png");

        // When
        UUID result = userService.updateUserDetails(userId, request);

        // Then
        assertThat(result).isEqualTo(userId);

        ArgumentCaptor<UserDetailsUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserDetailsUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserDetailsUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getDisplayName()).isEqualTo("John Doe");
        assertThat(event.getBio()).isEqualTo("Hiking enthusiast");
        assertThat(event.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void updateUserDetails_whenPartialFields_shouldPublishEventWithNullFields() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).username("johndoe").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserDetailsRequest request = new UserDetailsRequest("New Name", null, null);

        // When
        UUID result = userService.updateUserDetails(userId, request);

        // Then
        assertThat(result).isEqualTo(userId);

        ArgumentCaptor<UserDetailsUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserDetailsUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserDetailsUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getDisplayName()).isEqualTo("New Name");
        assertThat(event.getBio()).isNull();
        assertThat(event.getAvatarUrl()).isNull();
    }

    @Test
    void updateUserDetails_whenUserNotFound_shouldThrowException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UserDetailsRequest request = new UserDetailsRequest("Name", "Bio", "https://example.com");

        // When & Then
        assertThatThrownBy(() -> userService.updateUserDetails(userId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(eventPublisher, never()).publishEvent(any(UserDetailsUpdatedEvent.class));
    }
}
