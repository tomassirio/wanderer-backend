package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.UserIdentity;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.repository.UserIdentityRepository;
import com.tomassirio.wanderer.auth.service.SsoService;
import com.tomassirio.wanderer.auth.service.TokenService;
import com.tomassirio.wanderer.auth.service.UserProvisioningService;
import com.tomassirio.wanderer.auth.sso.ExternalIdentity;
import com.tomassirio.wanderer.auth.sso.UsernameGenerator;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsoServiceImpl implements SsoService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UserIdentityRepository userIdentityRepository;
    private final CredentialRepository credentialRepository;
    private final UserProvisioningService userProvisioningService;
    private final UsernameGenerator usernameGenerator;
    private final WandererQueryClient wandererQueryClient;
    private final TokenService tokenService;

    @Override
    public LoginResponse signIn(ExternalIdentity identity) {
        Credential credential =
                userIdentityRepository
                        .findByProviderAndSubject(identity.provider(), identity.subject())
                        .flatMap(link -> credentialRepository.findById(link.getUserId()))
                        .orElseGet(() -> linkOrProvision(identity));

        if (!credential.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }
        return tokenService.issueLoginTokens(
                loadUser(credential.getUserId()), credential.getRoles());
    }

    private Credential linkOrProvision(ExternalIdentity identity) {
        if (identity.email() == null || !identity.emailVerified()) {
            throw new IllegalArgumentException("SSO provider did not return a verified email");
        }
        Credential credential =
                credentialRepository
                        .findByEmail(identity.email())
                        .orElseGet(() -> provision(identity));

        userIdentityRepository.save(
                UserIdentity.builder()
                        .id(UUID.randomUUID())
                        .userId(credential.getUserId())
                        .provider(identity.provider())
                        .subject(identity.subject())
                        .email(identity.email())
                        .build());
        log.info("Linked {} identity to user {}", identity.provider(), credential.getUserId());
        return credential;
    }

    private Credential provision(ExternalIdentity identity) {
        String username = usernameGenerator.generate(identity.email());
        User user =
                userProvisioningService.provisionWithoutPassword(
                        username, identity.email(), displayName(identity, username));
        return credentialRepository
                .findById(user.getId())
                .orElseThrow(
                        () -> new IllegalStateException("Credential missing after provisioning"));
    }

    private static String displayName(ExternalIdentity identity, String fallback) {
        String name = identity.name();
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return name.length() > MAX_DISPLAY_NAME_LENGTH
                ? name.substring(0, MAX_DISPLAY_NAME_LENGTH)
                : name;
    }

    private User loadUser(UUID userId) {
        try {
            UserBasicInfo info = wandererQueryClient.getUserById(userId, "basic");
            User user = new User();
            user.setId(info.id());
            user.setUsername(info.username());
            return user;
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to fetch user information", e);
        }
    }
}
