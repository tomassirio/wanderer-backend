package com.tomassirio.wanderer.auth.service;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.RegisterPendingResponse;
import com.tomassirio.wanderer.auth.dto.RegisterRequest;
import java.time.Instant;
import java.util.UUID;

/**
 * Service interface for authentication operations. Provides methods for user login and
 * registration.
 *
 * @since 0.1.8
 */
public interface AuthService {

    /**
     * Authenticates a user with the provided username or email and password. If authentication is
     * successful, returns a JWT token and refresh token.
     *
     * @param identifier username or email of the user attempting to log in
     * @param password the password of the user
     * @param ipAddress the IP address of the client
     * @return a LoginResponse containing access token, refresh token, and metadata
     * @throws IllegalArgumentException if the credentials are invalid, account is disabled, or account is locked
     * @throws IllegalStateException if there is an issue contacting the user query service
     */
    LoginResponse login(String identifier, String password, String ipAddress);

    /**
     * Registers a new user with the provided registration details. Creates a pending email
     * verification instead of immediately creating the user. An email verification token is
     * generated and sent to the user's email address.
     *
     * @param request the registration request containing username, email, and password
     * @return a RegisterPendingResponse indicating the email verification was sent
     * @throws IllegalArgumentException if the email or username is already in use
     * @throws IllegalStateException if there is an issue with the registration process
     */
    RegisterPendingResponse register(RegisterRequest request);

    /**
     * Verifies a user's email address using the provided token. Upon successful verification,
     * creates the user in the domain via the command service and stores credentials in the auth
     * database.
     *
     * @param token the email verification token
     * @return a LoginResponse containing the JWT token and metadata
     * @throws IllegalArgumentException if the token is invalid, expired, or already verified
     * @throws IllegalStateException if user creation or credential saving fails
     */
    LoginResponse verifyEmail(String token);

    /**
     * Logs out a user by revoking the current access token and all refresh tokens.
     *
     * @param userId the user ID (extracted from authenticated user)
     * @param jti the JWT ID of the current access token
     * @param expiresAt when the access token expires
     */
    void logout(UUID userId, String jti, Instant expiresAt);

    /**
     * Initiates a password reset by creating a reset token and returning it. In a production
     * environment, this token should be sent via email.
     *
     * @param email the email address of the user requesting password reset
     * @return the password reset token
     * @throws IllegalArgumentException if no user is found with the provided email
     */
    String initiatePasswordReset(String email);

    /**
     * Completes a password reset by validating the token and updating the user's password.
     *
     * @param token the password reset token
     * @param newPassword the new password
     * @return the username of the user whose password was reset
     * @throws IllegalArgumentException if the token is invalid, expired, or already used
     */
    String resetPassword(String token, String newPassword);

    /**
     * Changes a user's password after verifying the current password.
     *
     * @param userId the user ID
     * @param currentPassword the current password
     * @param newPassword the new password
     * @throws IllegalArgumentException if the current password is incorrect
     */
    void changePassword(UUID userId, String currentPassword, String newPassword);
}
