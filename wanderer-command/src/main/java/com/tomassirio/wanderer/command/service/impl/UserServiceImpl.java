package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.client.WandererAuthClient;
import com.tomassirio.wanderer.command.controller.request.UserCreationRequest;
import com.tomassirio.wanderer.command.controller.request.UserDetailsRequest;
import com.tomassirio.wanderer.command.event.UserCreatedEvent;
import com.tomassirio.wanderer.command.event.UserDeletedEvent;
import com.tomassirio.wanderer.command.event.UserDetailsUpdatedEvent;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.command.service.UserService;
import com.tomassirio.wanderer.commons.domain.User;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@AllArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WandererAuthClient wandererAuthClient;
    private final ThumbnailService thumbnailService;

    @Override
    public UUID createUser(UserCreationRequest request) {
        String normalizedUsername = request.username().toLowerCase(Locale.ROOT);
        log.info("Creating user with username={} email={}", normalizedUsername, request.email());

        log.debug("Checking username uniqueness for {}", normalizedUsername);
        Optional<User> byUsername = userRepository.findByUsername(normalizedUsername);
        if (byUsername.isPresent()) {
            log.warn("Username already in use: {}", normalizedUsername);
            throw new IllegalArgumentException("Username already in use");
        }

        // Pre-generate ID
        UUID userId = UUID.randomUUID();

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                UserCreatedEvent.builder()
                        .userId(userId)
                        .username(normalizedUsername)
                        .displayName(request.displayName())
                        .build());

        log.info("User created with id={}", userId);
        return userId;
    }

    @Override
    public void deleteUser(UUID userId) {
        log.info("Deleting user with id={}", userId);

        userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found with id: " + userId));

        // Delete all local user data
        eventPublisher.publishEvent(UserDeletedEvent.builder().userId(userId).build());

        // Delete credentials from auth service (best-effort)
        try {
            wandererAuthClient.deleteCredentials(userId);
            log.info("Deleted credentials from auth service for user: {}", userId);
        } catch (FeignException.BadRequest | FeignException.NotFound e) {
            log.warn(
                    "User {} not found in auth service, skipping credential deletion: {}",
                    userId,
                    e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete credentials from auth service for user: {}", userId, e);
            throw new RuntimeException(
                    "Failed to delete credentials from auth service: " + e.getMessage(), e);
        }

        log.info("User deletion completed for id={}", userId);
    }

    @Override
    public void deleteUserData(UUID userId) {
        log.info("Deleting local data for user with id={}", userId);

        userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found with id: " + userId));

        eventPublisher.publishEvent(UserDeletedEvent.builder().userId(userId).build());

        log.info("User data deletion processed for id={}", userId);
    }

    @Override
    public UUID updateUserDetails(UUID userId, UserDetailsRequest request) {
        log.info("Updating user details for userId={}", userId);

        userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found with id: " + userId));

        // Publish event - persistence handler will write to DB
        eventPublisher.publishEvent(
                UserDetailsUpdatedEvent.builder()
                        .userId(userId)
                        .displayName(request.displayName())
                        .bio(request.bio())
                        .build());

        log.info("Accepted user details update for userId={}", userId);
        return userId;
    }

    @Override
    public UUID updateAvatar(UUID userId, MultipartFile file) {
        log.info("Updating avatar for userId={}", userId);

        userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found with id: " + userId));

        // Process and save the profile picture
        thumbnailService.processAndSaveProfilePicture(userId, file);

        // Publish event so subscribers are notified (though avatar URL is now computed)
        eventPublisher.publishEvent(UserDetailsUpdatedEvent.builder().userId(userId).build());

        log.info("Accepted avatar update for userId={}", userId);
        return userId;
    }

    @Override
    public UUID deleteAvatar(UUID userId) {
        log.info("Deleting avatar for userId={}", userId);

        userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found with id: " + userId));

        // Delete the avatar file
        thumbnailService.deleteThumbnail(userId, ThumbnailEntityType.USER_PROFILE);

        log.info("Accepted avatar deletion for userId={}", userId);
        return userId;
    }
}
