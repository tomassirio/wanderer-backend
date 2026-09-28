package com.tomassirio.wanderer.auth.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tomassirio.wanderer.auth.AuthApplication;
import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.EmailVerificationToken;
import com.tomassirio.wanderer.auth.domain.UserIdentity;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import com.tomassirio.wanderer.commons.security.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = AuthApplication.class)
@Import(TestConfig.class)
@TestPropertySource(
        properties =
                "jwt.secret=test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum")
class SsoSchemaIT extends BaseIntegrationTest {

    @MockitoBean private WandererCommandClient wandererCommandClient;
    @MockitoBean private WandererQueryClient wandererQueryClient;

    @Autowired private CredentialRepository credentialRepository;
    @Autowired private UserIdentityRepository userIdentityRepository;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    void credentialWithoutPasswordHash_canBeSaved() {
        UUID userId = UUID.randomUUID();
        credentialRepository.saveAndFlush(
                Credential.builder()
                        .userId(userId)
                        .email(userId + "@sso.test")
                        .enabled(true)
                        .roles(Set.of(Role.USER))
                        .build());

        assertNull(credentialRepository.findById(userId).orElseThrow().getPasswordHash());
    }

    @Test
    void userIdentity_isUniquePerProviderAndSubject() {
        UUID userId = UUID.randomUUID();
        credentialRepository.saveAndFlush(
                Credential.builder()
                        .userId(userId)
                        .email(userId + "@sso.test")
                        .enabled(true)
                        .roles(Set.of(Role.USER))
                        .build());
        userIdentityRepository.saveAndFlush(identity(userId, "google", "sub-" + userId));

        assertTrue(
                userIdentityRepository
                        .findByProviderAndSubject("google", "sub-" + userId)
                        .isPresent());
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        userIdentityRepository.saveAndFlush(
                                identity(userId, "google", "sub-" + userId)));
    }

    @Test
    void findByEmailIgnoreCase_matchesDifferentlyCasedEmail() {
        UUID userId = UUID.randomUUID();
        credentialRepository.saveAndFlush(
                Credential.withPassword(userId, "Ana." + userId + "@Gmail.com", "$2a$hash"));

        assertEquals(
                userId,
                credentialRepository
                        .findByEmailIgnoreCase("ana." + userId + "@gmail.com")
                        .orElseThrow()
                        .getUserId());
    }

    @Test
    void emailVerificationToken_stillRequiresPasswordHash() {
        EmailVerificationToken token =
                EmailVerificationToken.builder()
                        .tokenId(UUID.randomUUID())
                        .email("pending@test.com")
                        .username("pending")
                        .passwordHash(null)
                        .tokenHash("hash-" + UUID.randomUUID())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .verified(false)
                        .build();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> emailVerificationTokenRepository.saveAndFlush(token));
    }

    private UserIdentity identity(UUID userId, String provider, String subject) {
        return UserIdentity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider(provider)
                .subject(subject)
                .email(userId + "@sso.test")
                .build();
    }
}
