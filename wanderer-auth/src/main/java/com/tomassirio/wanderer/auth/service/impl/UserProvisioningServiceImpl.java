package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.service.UserProvisioningService;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private final CredentialRepository credentialRepository;
    private final WandererCommandClient wandererCommandClient;
    private final WandererQueryClient wandererQueryClient;

    @Override
    public User provisionWithPassword(
            String username, String email, String displayName, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password is required for password-based registration");
        }
        return provision(
                username,
                email,
                displayName,
                userId -> Credential.withPassword(userId, email, passwordHash));
    }

    @Override
    public User provisionWithoutPassword(String username, String email, String displayName) {
        return provision(username, email, displayName, userId -> Credential.ssoOnly(userId, email));
    }

    private User provision(
            String username,
            String email,
            String displayName,
            Function<UUID, Credential> credentialFactory) {
        // 1) Create the domain user via the command service (returns UUID)
        var payload = Map.of("username", username, "email", email, "displayName", displayName);
        UUID createdUserId;
        try {
            createdUserId = wandererCommandClient.createUser(payload);
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to create user in command service", e);
        }

        // 2) Fetch the created user from query service to get full User object
        User createdUser;
        try {
            UserBasicInfo userInfo = wandererQueryClient.getUserById(createdUserId, "basic");
            createdUser = new User();
            createdUser.setId(userInfo.id());
            createdUser.setUsername(userInfo.username());
        } catch (FeignException e) {
            try {
                wandererCommandClient.deleteUser(createdUserId);
            } catch (FeignException ex) {
                throw new IllegalStateException(
                        "Failed to fetch created user and failed to rollback: " + ex.getMessage(),
                        e);
            }
            throw new IllegalStateException("Failed to fetch created user from query service", e);
        }

        // 3) Create credential in auth DB — compensate on failure
        try {
            if (credentialRepository.findById(createdUser.getId()).isPresent()) {
                throw new IllegalArgumentException(
                        "Credentials already exist for user: " + createdUser.getId());
            }
            credentialRepository.save(credentialFactory.apply(createdUser.getId()));
        } catch (Exception e) {
            try {
                wandererCommandClient.deleteUser(createdUserId);
            } catch (FeignException ex) {
                throw new IllegalStateException(
                        "Failed to create credentials and failed to rollback user creation: "
                                + ex.getMessage(),
                        e);
            }
            throw new IllegalStateException(
                    "Failed to create credentials, rolled back user creation", e);
        }
        return createdUser;
    }
}
