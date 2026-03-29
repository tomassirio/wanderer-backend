package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.EmailVerificationToken;
import com.tomassirio.wanderer.auth.domain.PasswordResetToken;
import com.tomassirio.wanderer.auth.domain.RefreshToken;
import com.tomassirio.wanderer.auth.dto.RefreshTokenResponse;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.repository.EmailVerificationTokenRepository;
import com.tomassirio.wanderer.auth.repository.PasswordResetTokenRepository;
import com.tomassirio.wanderer.auth.repository.RefreshTokenRepository;
import com.tomassirio.wanderer.auth.service.JwtService;
import com.tomassirio.wanderer.auth.service.TokenService;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import com.tomassirio.wanderer.commons.security.Role;
import feign.FeignException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for token management operations. Handles refresh tokens and password reset
 * tokens.
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long EMAIL_VERIFICATION_TOKEN_EXPIRY_SECONDS = 86400; // 24 hours

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final CredentialRepository credentialRepository;
    private final JwtService jwtService;
    private final WandererQueryClient wandererQueryClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String createRefreshToken(UUID userId) {
        // Generate a secure random token
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Hash the token before storing
        String tokenHash = hashToken(token);

        // Calculate expiration
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());

        // Create and save refresh token
        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(userId)
                        .tokenHash(tokenHash)
                        .expiresAt(expiresAt)
                        .revoked(false)
                        .build();

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        // Hash the provided token to find it in the database
        String tokenHash = hashToken(refreshToken);

        // Find the refresh token
        Optional<RefreshToken> maybeToken = refreshTokenRepository.findByTokenHash(tokenHash);
        if (maybeToken.isEmpty()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        RefreshToken storedToken = maybeToken.get();

        // Validate token
        if (storedToken.isRevoked()) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token has expired");
        }

        // Get user information
        User user;
        try {
            UserBasicInfo userInfo = wandererQueryClient.getUserById(storedToken.getUserId(), "basic");
            user = new User();
            user.setId(userInfo.id());
            user.setUsername(userInfo.username());
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to fetch user information", e);
        }

        // Get user roles from credentials
        Optional<Credential> maybeCred = credentialRepository.findById(user.getId());
        Set<Role> roles = maybeCred.map(Credential::getRoles).orElse(Set.of(Role.USER));

        // Generate new access token with JTI and roles
        String jti = UUID.randomUUID().toString();
        String newAccessToken = jwtService.generateTokenWithJti(user, jti, roles);

        // Generate new refresh token (token rotation)
        String newRefreshToken = createRefreshToken(user.getId());

        // Revoke old refresh token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return new RefreshTokenResponse(
                newAccessToken, newRefreshToken, "Bearer", jwtService.getExpirationMs());
    }

    @Override
    @Transactional
    public void revokeAllRefreshTokensForUser(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    @Override
    @Transactional
    public String createPasswordResetToken(UUID userId) {
        // Generate a secure random token
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Hash the token before storing
        String tokenHash = hashToken(token);

        // Calculate expiration (1 hour)
        Instant expiresAt = Instant.now().plusSeconds(3600);

        // Create and save password reset token
        PasswordResetToken resetToken =
                PasswordResetToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(userId)
                        .tokenHash(tokenHash)
                        .expiresAt(expiresAt)
                        .used(false)
                        .build();

        passwordResetTokenRepository.save(resetToken);

        return token;
    }

    @Override
    public UUID validatePasswordResetToken(String token) {
        // Hash the provided token to find it in the database
        String tokenHash = hashToken(token);

        // Find the reset token
        Optional<PasswordResetToken> maybeToken =
                passwordResetTokenRepository.findByTokenHash(tokenHash);
        if (maybeToken.isEmpty()) {
            throw new IllegalArgumentException("Invalid password reset token");
        }

        PasswordResetToken resetToken = maybeToken.get();

        // Validate token
        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Password reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Password reset token has expired");
        }

        return resetToken.getUserId();
    }

    @Override
    @Transactional
    public void markPasswordResetTokenAsUsed(String token) {
        String tokenHash = hashToken(token);
        Optional<PasswordResetToken> maybeToken =
                passwordResetTokenRepository.findByTokenHash(tokenHash);
        if (maybeToken.isPresent()) {
            PasswordResetToken resetToken = maybeToken.get();
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
        }
    }

    @Override
    @Transactional
    public String createEmailVerificationToken(String email, String username, String passwordHash) {
        // Generate a secure random token
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Hash the token before storing
        String tokenHash = hashToken(token);

        // Calculate expiration (24 hours)
        Instant expiresAt = Instant.now().plusSeconds(EMAIL_VERIFICATION_TOKEN_EXPIRY_SECONDS);

        // Create and save email verification token
        EmailVerificationToken verificationToken =
                EmailVerificationToken.builder()
                        .tokenId(UUID.randomUUID())
                        .email(email)
                        .username(username)
                        .passwordHash(passwordHash)
                        .tokenHash(tokenHash)
                        .expiresAt(expiresAt)
                        .verified(false)
                        .build();

        emailVerificationTokenRepository.save(verificationToken);

        return token;
    }

    @Override
    public String[] validateEmailVerificationToken(String token) {
        // Hash the provided token to find it in the database
        String tokenHash = hashToken(token);

        // Find the verification token
        Optional<EmailVerificationToken> maybeToken =
                emailVerificationTokenRepository.findByTokenHash(tokenHash);
        if (maybeToken.isEmpty()) {
            throw new IllegalArgumentException("Invalid email verification token");
        }

        EmailVerificationToken verificationToken = maybeToken.get();

        // Validate token
        if (verificationToken.isVerified()) {
            throw new IllegalArgumentException(
                    "Email verification token has already been verified");
        }

        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Email verification token has expired");
        }

        return new String[] {
            verificationToken.getEmail(),
            verificationToken.getUsername(),
            verificationToken.getPasswordHash()
        };
    }

    @Override
    @Transactional
    public void markEmailVerificationTokenAsVerified(String token) {
        String tokenHash = hashToken(token);
        Optional<EmailVerificationToken> maybeToken =
                emailVerificationTokenRepository.findByTokenHash(tokenHash);
        if (maybeToken.isPresent()) {
            EmailVerificationToken verificationToken = maybeToken.get();
            verificationToken.setVerified(true);
            emailVerificationTokenRepository.save(verificationToken);
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
