package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.event.AchievementUnlockedEvent;
import com.tomassirio.wanderer.command.repository.AchievementRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.UserAchievementRepository;
import com.tomassirio.wanderer.command.service.AchievementService;
import com.tomassirio.wanderer.command.service.impl.checker.AchievementChecker;
import com.tomassirio.wanderer.command.service.impl.checker.SocialAchievementChecker;
import com.tomassirio.wanderer.command.service.impl.checker.TripAchievementChecker;
import com.tomassirio.wanderer.commons.domain.Achievement;
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.Trip;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator that delegates achievement checking to pluggable {@link AchievementChecker}
 * strategies.
 *
 * <p>Adding a new achievement category requires only a new {@code @Component} implementing {@link
 * TripAchievementChecker} or {@link SocialAchievementChecker} — no changes to this class.
 *
 * <p>Achievement checking runs asynchronously to avoid blocking main request flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private final TripRepository tripRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementRepository achievementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final List<TripAchievementChecker> tripCheckers;
    private final List<SocialAchievementChecker> socialCheckers;

    /**
     * Checks and unlocks trip-scoped achievements after a new update is added.
     * Runs asynchronously to avoid blocking the main request.
     *
     * @param tripId the trip ID to check achievements for
     */
    @Override
    @Async
    @Transactional
    public void checkAndUnlockAchievements(UUID tripId) {
        Trip trip =
                tripRepository
                        .findById(tripId)
                        .orElseThrow(() -> new RuntimeException("Trip not found"));

        evaluate(tripCheckers, trip, trip.getUserId(), trip.getId());
    }

    /**
     * Checks and unlocks social achievements for a user (followers and friends).
     * Runs asynchronously to avoid blocking the main request.
     *
     * @param userId the user ID to check achievements for
     */
    @Override
    @Async
    @Transactional
    public void checkAndUnlockSocialAchievements(UUID userId) {
        evaluate(socialCheckers, userId, userId, null);
    }

    /**
     * Evaluates a list of checkers against a context and unlocks any achievements whose thresholds
     * are met.
     *
     * @param checkers the achievement checkers to evaluate
     * @param context the context passed to each checker's {@code computeMetric}
     * @param userId the user who earns the achievement
     * @param tripId the trip ID (or {@code null} for social achievements)
     * @param <T> the context type
     */
    private <T> void evaluate(
            List<? extends AchievementChecker<T>> checkers, T context, UUID userId, UUID tripId) {
        for (AchievementChecker<T> checker : checkers) {
            double metric = checker.computeMetric(context);
            for (AchievementType type : checker.getApplicableTypes()) {
                if (metric >= type.getThreshold()) {
                    unlockIfNotExists(userId, type, tripId, metric);
                }
            }
        }
    }

    private void unlockIfNotExists(UUID userId, AchievementType type, UUID tripId, double value) {
        boolean exists =
                userAchievementRepository.existsByUserIdAndAchievementTypeAndOptionalTripId(
                        userId, type, tripId);

        if (exists) {
            log.debug("Achievement {} already unlocked for user {}", type, userId);
            return;
        }

        Achievement achievement = getOrCreateAchievement(type);

        eventPublisher.publishEvent(
                AchievementUnlockedEvent.builder()
                        .userAchievementId(UUID.randomUUID())
                        .userId(userId)
                        .achievementId(achievement.getId())
                        .tripId(tripId)
                        .achievementType(type)
                        .achievementName(achievement.getName())
                        .valueAchieved(value)
                        .unlockedAt(Instant.now())
                        .build());

        log.info("Achievement {} unlocked for user {}", type, userId);
    }

    private Achievement getOrCreateAchievement(AchievementType type) {
        return achievementRepository
                .findByTypeAndEnabledTrue(type)
                .orElseGet(
                        () ->
                                achievementRepository.save(
                                        Achievement.builder()
                                                .id(UUID.randomUUID())
                                                .type(type)
                                                .name(type.getName())
                                                .description(type.getDescription())
                                                .thresholdValue(type.getThreshold())
                                                .enabled(true)
                                                .build()));
    }
}
