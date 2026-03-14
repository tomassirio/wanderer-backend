package com.tomassirio.wanderer.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.tomassirio.wanderer.command.event.UserCreatedEvent;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.commons.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCreatedEventHandlerTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserCreatedEventHandler handler;

    @Test
    void handle_shouldPersistUser() {
        UUID userId = UUID.randomUUID();
        String username = "johndoe";

        UserCreatedEvent event =
                UserCreatedEvent.builder().userId(userId).username(username).build();

        handler.handle(event);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getUsername()).isEqualTo(username);
        assertThat(saved.getUserDetails()).isNull();
    }

    @Test
    void handle_whenDisplayNameProvided_shouldPersistUserWithDisplayName() {
        UUID userId = UUID.randomUUID();
        String username = "johndoe";
        String displayName = "JohnDoe";

        UserCreatedEvent event =
                UserCreatedEvent.builder()
                        .userId(userId)
                        .username(username)
                        .displayName(displayName)
                        .build();

        handler.handle(event);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getUsername()).isEqualTo(username);
        assertThat(saved.getUserDetails()).isNotNull();
        assertThat(saved.getUserDetails().getDisplayName()).isEqualTo(displayName);
    }
}
