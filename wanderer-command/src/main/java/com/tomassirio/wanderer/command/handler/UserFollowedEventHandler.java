package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.UserFollowedEvent;
import com.tomassirio.wanderer.command.handler.support.AfterCommit;
import com.tomassirio.wanderer.command.repository.UserFollowRepository;
import com.tomassirio.wanderer.command.service.AchievementService;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for persisting user follow events to the database.
 *
 * <p>This handler implements the CQRS write side by handling UserFollowedEvent and persisting the
 * follow relationship to the database. WebSocket broadcasting is handled centrally by {@link
 * com.tomassirio.wanderer.command.websocket.BroadcastableEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserFollowedEventHandler implements EventHandler<UserFollowedEvent> {

    private final UserFollowRepository userFollowRepository;
    private final AchievementService achievementCalculationService;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(UserFollowedEvent event) {
        log.debug(
                "Persisting UserFollowedEvent: {} follows {}",
                event.getFollowerId(),
                event.getFollowedId());

        UserFollow follow =
                UserFollow.builder()
                        .id(event.getFollowId())
                        .followerId(event.getFollowerId())
                        .followedId(event.getFollowedId())
                        .createdAt(event.getCreatedAt())
                        .build();

        userFollowRepository.save(follow);
        log.info(
                "User follow created and persisted: {} follows {}",
                event.getFollowerId(),
                event.getFollowedId());

        // Check follower achievements for the followed user
        AfterCommit.run(
                () ->
                        achievementCalculationService.checkAndUnlockSocialAchievements(
                                event.getFollowedId()));
    }
}
