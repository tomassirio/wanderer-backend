package com.tomassirio.wanderer.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.event.UserDetailsUpdatedEvent;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDetailsUpdatedEventHandlerTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserDetailsUpdatedEventHandler handler;

    @Test
    void handle_whenUserExists_shouldUpdateAllFields() {
        // Given
        UUID userId = UUID.randomUUID();
        UserDetailsUpdatedEvent event =
                UserDetailsUpdatedEvent.builder()
                        .userId(userId)
                        .displayName("John Doe")
                        .bio("Walking the Camino")
                        .build();

        UserDetails existingDetails =
                UserDetails.builder().displayName("Old Name").bio("Old bio").build();
        User user =
                User.builder().id(userId).username("johndoe").userDetails(existingDetails).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        handler.handle(event);

        // Then
        assertThat(user.getUserDetails().getDisplayName()).isEqualTo("John Doe");
        assertThat(user.getUserDetails().getBio()).isEqualTo("Walking the Camino");
    }

    @Test
    void handle_whenPartialUpdate_shouldOnlyUpdateProvidedFields() {
        // Given
        UUID userId = UUID.randomUUID();
        UserDetailsUpdatedEvent event =
                UserDetailsUpdatedEvent.builder()
                        .userId(userId)
                        .displayName("New Name")
                        .bio(null)
                        .build();

        UserDetails existingDetails =
                UserDetails.builder().displayName("Old Name").bio("Keep this bio").build();
        User user =
                User.builder().id(userId).username("johndoe").userDetails(existingDetails).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        handler.handle(event);

        // Then
        assertThat(user.getUserDetails().getDisplayName()).isEqualTo("New Name");
        assertThat(user.getUserDetails().getBio()).isEqualTo("Keep this bio");
    }

    @Test
    void handle_whenUserDetailsIsNull_shouldCreateNewDetails() {
        // Given
        UUID userId = UUID.randomUUID();
        UserDetailsUpdatedEvent event =
                UserDetailsUpdatedEvent.builder()
                        .userId(userId)
                        .displayName("John")
                        .bio("Bio text")
                        .build();

        User user = User.builder().id(userId).username("johndoe").build();
        user.setUserDetails(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        handler.handle(event);

        // Then
        assertThat(user.getUserDetails()).isNotNull();
        assertThat(user.getUserDetails().getDisplayName()).isEqualTo("John");
        assertThat(user.getUserDetails().getBio()).isEqualTo("Bio text");
    }

    @Test
    void handle_whenUserNotFound_shouldDoNothing() {
        // Given
        UUID userId = UUID.randomUUID();
        UserDetailsUpdatedEvent event =
                UserDetailsUpdatedEvent.builder().userId(userId).displayName("John").build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        handler.handle(event);

        // Then
        verify(userRepository).findById(userId);
    }
}
