package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.UserCreatedEvent;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventHandler implements EventHandler<UserCreatedEvent> {

    private final UserRepository userRepository;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(UserCreatedEvent event) {
        log.debug("Persisting UserCreatedEvent for user: {}", event.getUserId());

        User.UserBuilder builder =
                User.builder().id(event.getUserId()).username(event.getUsername());

        if (event.getDisplayName() != null) {
            builder.userDetails(UserDetails.builder().displayName(event.getDisplayName()).build());
        }

        userRepository.save(builder.build());
        log.info("User created and persisted: {}", event.getUserId());
    }
}
