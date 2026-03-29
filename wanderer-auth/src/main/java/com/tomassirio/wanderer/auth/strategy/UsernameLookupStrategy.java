package com.tomassirio.wanderer.auth.strategy;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for looking up users by username. Normalizes username to lowercase and queries the user
 * service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsernameLookupStrategy implements UserLookupStrategy {

    private final WandererQueryClient wandererQueryClient;

    @Override
    public boolean canHandle(String identifier) {
        // Username strategy handles anything that's not an email
        return identifier != null && !identifier.contains("@");
    }

    @Override
    public Optional<User> lookupUser(String identifier) {
        String normalizedUsername = identifier.toLowerCase(Locale.ROOT);
        try {
            UserBasicInfo userInfo = wandererQueryClient.getUserByUsername(normalizedUsername, "basic");
            if (userInfo == null) {
                return Optional.empty();
            }
            // Convert UserBasicInfo to User entity for authentication
            User user = new User();
            user.setId(userInfo.id());
            user.setUsername(userInfo.username());
            return Optional.of(user);
        } catch (FeignException e) {
            if (e.status() == 404) {
                log.debug("User not found by username: {}", normalizedUsername);
                return Optional.empty();
            }
            throw new IllegalStateException("Failed to contact user query service", e);
        }
    }
}
