package com.tomassirio.wanderer.auth.strategy;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.commons.domain.User;
import feign.FeignException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for looking up users by email address. Checks credentials table for email, then fetches
 * user details.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailLookupStrategy implements UserLookupStrategy {

    private final CredentialRepository credentialRepository;
    private final WandererQueryClient wandererQueryClient;

    @Override
    public boolean canHandle(String identifier) {
        return identifier != null && identifier.contains("@");
    }

    @Override
    public Optional<User> lookupUser(String identifier) {
        return credentialRepository
                .findByEmail(identifier)
                .flatMap(
                        credential -> {
                            UUID userId = credential.getUserId();
                            try {
                                User user = wandererQueryClient.getUserById(userId);
                                return Optional.ofNullable(user);
                            } catch (FeignException e) {
                                log.debug(
                                        "User not found in query service for email lookup: {}",
                                        userId);
                                return Optional.empty();
                            }
                        });
    }
}
