package com.tomassirio.wanderer.auth.service;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.RefreshTokenResponse;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.security.Role;
import java.util.Set;
import java.util.UUID;

/**
 * Service interface for token management operations. Provides methods for managing refresh tokens
 * and password reset tokens.
 *
 * @since 0.4.1
 */
public interface TokenService {

    /**
     * Creates a refresh token for the given user.
     *
     * @param userId the user ID
     * @return the generated refresh token
     */
    String createRefreshToken(UUID userId);

    /**
     * Validates and refreshes the access token using the provided refresh token.
     *
     * @param refreshToken the refresh token
     * @return a RefreshTokenResponse containing new access and refresh tokens
     * @throws IllegalArgumentException if the refresh token is invalid, expired, or revoked
     */
    RefreshTokenResponse refreshAccessToken(String refreshToken);

    /**
     * Revokes all refresh tokens for a user (used for logout all devices).
     *
     * @param userId the user ID
     */
    void revokeAllRefreshTokensForUser(UUID userId);

    /**
     * Creates a password reset token for the given user.
     *
     * @param userId the user ID
     * @return the generated password reset token
     */
    String createPasswordResetToken(UUID userId);

    /**
     * Validates a password reset token and returns the associated user ID.
     *
     * @param token the password reset token
     * @return the user ID associated with the token
     * @throws IllegalArgumentException if the token is invalid, expired, or already used
     */
    UUID validatePasswordResetToken(String token);

    /**
     * Marks a password reset token as used.
     *
     * @param token the password reset token
     */
    void markPasswordResetTokenAsUsed(String token);

    /**
     * Creates an email verification token for the given email, username, and password.
     *
     * @param email the email address
     * @param username the username
     * @param passwordHash the hashed password
     * @return the generated email verification token
     */
    String createEmailVerificationToken(String email, String username, String passwordHash);

    /**
     * Validates an email verification token and returns the associated email verification data.
     *
     * @param token the email verification token
     * @return the email, username, and password hash as a string array [email, username,
     *     passwordHash]
     * @throws IllegalArgumentException if the token is invalid, expired, or already verified
     */
    String[] validateEmailVerificationToken(String token);

    /**
     * Marks an email verification token as verified.
     *
     * @param token the email verification token
     */
    void markEmailVerificationTokenAsVerified(String token);

    /**
     * Issues a fresh access token (with JTI) and refresh token for a user.
     *
     * @param user the authenticated user (id and username required)
     * @param roles roles to embed in the access token
     * @return a Bearer LoginResponse
     */
    LoginResponse issueLoginTokens(User user, Set<Role> roles);
}
