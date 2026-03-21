package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.UserDetailsUpdatedEvent;
import com.tomassirio.wanderer.command.repository.UserRepository;
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
public class UserDetailsUpdatedEventHandler implements EventHandler<UserDetailsUpdatedEvent> {

    private final UserRepository userRepository;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(UserDetailsUpdatedEvent event) {
        log.debug("Persisting UserDetailsUpdatedEvent for user: {}", event.getUserId());

        userRepository
                .findById(event.getUserId())
                .ifPresent(
                        user -> {
                            UserDetails details = user.getUserDetails();
                            if (details == null) {
                                details = new UserDetails();
                            }

                            if (event.getDisplayName() != null) {
                                details.setDisplayName(event.getDisplayName());
                            }
                            if (event.getBio() != null) {
                                details.setBio(event.getBio());
                            }

                            user.setUserDetails(details);
                            log.info("User details updated for user: {}", event.getUserId());
                        });
    }
}
