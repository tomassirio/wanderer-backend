package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.EmailAddresses;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.RegisterPendingResponse;
import com.tomassirio.wanderer.auth.dto.RegisterRequest;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.service.AuthService;
import com.tomassirio.wanderer.auth.service.EmailService;
import com.tomassirio.wanderer.auth.service.JwtService;
import com.tomassirio.wanderer.auth.service.LoginAttemptService;
import com.tomassirio.wanderer.auth.service.TokenService;
import com.tomassirio.wanderer.auth.service.UserProvisioningService;
import com.tomassirio.wanderer.auth.strategy.UserLookupStrategy;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import com.tomassirio.wanderer.commons.security.Role;
import com.tomassirio.wanderer.commons.security.revocation.RevokedTokenCache;
import feign.FeignException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service implementation for authentication operations. Handles user login and registration using
 * Feign clients for inter-service communication.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final UserProvisioningService userProvisioningService;
    private final WandererQueryClient wandererQueryClient;
    private final List<UserLookupStrategy> userLookupStrategies;
    private final RevokedTokenCache revokedTokenCache;
    private final LoginAttemptService loginAttemptService;

    /**
     * Verify credentials and return access token and refresh token when valid. Supports login with
     * either username or email. Tracks login attempts for brute-force protection.
     *
     * @param identifier username or email address
     * @param password user password
     * @param ipAddress the IP address of the client
     * @return LoginResponse with tokens
     * @throws IllegalArgumentException when credentials are invalid or account is locked
     */
    public LoginResponse login(String identifier, String password, String ipAddress) {
        // Check if account is locked due to failed attempts
        if (loginAttemptService.isAccountLocked(identifier)) {
            loginAttemptService.recordFailedLogin(identifier, ipAddress);
            throw new IllegalArgumentException(
                    "Account temporarily locked due to too many failed login attempts. Please try again later.");
        }

        User user;
        try {
            // Find the appropriate strategy to lookup the user
            user =
                    userLookupStrategies.stream()
                            .filter(strategy -> strategy.canHandle(identifier))
                            .findFirst()
                            .flatMap(strategy -> strategy.lookupUser(identifier))
                            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        } catch (IllegalArgumentException e) {
            loginAttemptService.recordFailedLogin(identifier, ipAddress);
            throw e;
        }

        // Find credentials by user id in the auth database
        Optional<Credential> maybeCred = credentialRepository.findById(user.getId());
        if (maybeCred.isEmpty()) {
            loginAttemptService.recordFailedLogin(identifier, ipAddress);
            throw new IllegalArgumentException("Invalid credentials");
        }
        Credential cred = maybeCred.get();

        if (!cred.isEnabled()) {
            loginAttemptService.recordFailedLogin(identifier, ipAddress);
            throw new IllegalArgumentException("Account disabled");
        }

        if (!cred.hasPassword() || !passwordEncoder.matches(password, cred.getPasswordHash())) {
            loginAttemptService.recordFailedLogin(identifier, ipAddress);
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Generate tokens
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtService.generateTokenWithJti(user, jti, cred.getRoles());
        String refreshToken = tokenService.createRefreshToken(user.getId());

        // Record successful login
        loginAttemptService.recordSuccessfulLogin(identifier, user.getId(), ipAddress);

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getExpirationMs(),
                user.getUsername());
    }

    /**
     * Register a new user by creating a pending email verification. Instead of immediately creating
     * the user, this generates a verification token and sends it via email. The user account is
     * only created after email verification.
     */
    public RegisterPendingResponse register(RegisterRequest request) {
        // Normalize username to lowercase for case-insensitive uniqueness
        String normalizedUsername = request.username().toLowerCase(Locale.ROOT);
        String normalizedEmail = EmailAddresses.normalize(request.email());

        // Check if email is already in use
        if (credentialRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email already in use: " + normalizedEmail);
        }

        // Check if username is already taken by querying the read side
        try {
            UserBasicInfo existingUser =
                    wandererQueryClient.getUserByUsername(normalizedUsername, "basic");
            if (existingUser != null) {
                throw new IllegalArgumentException("Username already taken: " + normalizedUsername);
            }
        } catch (FeignException e) {
            // 404 is expected if username doesn't exist - this is good
            if (e.status() != 404) {
                throw new IllegalStateException("Failed to check username availability", e);
            }
        }

        // Hash the password
        String passwordHash = passwordEncoder.encode(request.password());

        // Create email verification token with original username preserved
        String verificationToken =
                tokenService.createEmailVerificationToken(
                        normalizedEmail, request.username(), passwordHash);

        // Send verification email with original-cased username
        emailService.sendVerificationEmail(normalizedEmail, request.username(), verificationToken);

        return new RegisterPendingResponse(
                "Registration pending. Please check your email to verify your account.");
    }

    /**
     * Verify email and complete user registration. Validates the verification token, provisions the
     * user with the password chosen at registration, and returns login tokens.
     */
    public LoginResponse verifyEmail(String token) {
        String[] verificationData = tokenService.validateEmailVerificationToken(token);
        // Normalize even though the write path already does; covers tokens created before this
        // change.
        String email = EmailAddresses.normalize(verificationData[0]);
        String originalUsername = verificationData[1];
        String passwordHash = verificationData[2];

        // Normalize username to lowercase; keep original casing as displayName
        String username = originalUsername.toLowerCase(Locale.ROOT);

        if (credentialRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email already in use: " + email);
        }

        User createdUser =
                userProvisioningService.provisionWithPassword(
                        username, email, originalUsername, passwordHash);

        tokenService.markEmailVerificationTokenAsVerified(token);
        return tokenService.issueLoginTokens(createdUser, Set.of(Role.USER));
    }

    @Override
    public void logout(UUID userId, String jti, Instant expiresAt) {
        // Revoke all refresh tokens
        tokenService.revokeAllRefreshTokensForUser(userId);

        // Add current access token JTI to Redis blacklist
        if (jti != null && expiresAt != null) {
            long secondsUntilExpiry = Duration.between(Instant.now(), expiresAt).getSeconds();
            if (secondsUntilExpiry > 0) {
                revokedTokenCache.revokeToken(jti, secondsUntilExpiry);
            }
        }
    }

    @Override
    public String initiatePasswordReset(String email) {
        // Find credential by email
        String normalizedEmail = EmailAddresses.normalize(email);
        Optional<Credential> maybeCred = credentialRepository.findByEmail(normalizedEmail);
        if (maybeCred.isEmpty()) {
            throw new IllegalArgumentException("No user found with the provided email");
        }

        Credential cred = maybeCred.get();
        String resetToken = tokenService.createPasswordResetToken(cred.getUserId());

        // Fetch the user to get the username for the email
        String username;
        try {
            UserBasicInfo userInfo = wandererQueryClient.getUserById(cred.getUserId(), "basic");
            username = userInfo.username();
        } catch (FeignException e) {
            // Fall back to email as the greeting name if user lookup fails
            username = normalizedEmail;
        }

        // Send password reset email
        emailService.sendPasswordResetEmail(normalizedEmail, username, resetToken);

        return resetToken;
    }

    @Override
    public String resetPassword(String token, String newPassword) {
        // Validate the reset token and get user ID
        UUID userId = tokenService.validatePasswordResetToken(token);

        // Find the credential
        Optional<Credential> maybeCred = credentialRepository.findById(userId);
        if (maybeCred.isEmpty()) {
            throw new IllegalStateException("Credential not found for user");
        }

        Credential cred = maybeCred.get();

        // Update the password
        String hashedPassword = passwordEncoder.encode(newPassword);
        cred.setPasswordHash(hashedPassword);
        credentialRepository.save(cred);

        // Mark the token as used
        tokenService.markPasswordResetTokenAsUsed(token);

        // Revoke all refresh tokens for security
        tokenService.revokeAllRefreshTokensForUser(userId);

        // Fetch the username for the response
        try {
            UserBasicInfo userInfo = wandererQueryClient.getUserById(userId, "basic");
            return userInfo.username();
        } catch (FeignException e) {
            return null;
        }
    }

    @Override
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        // Find the credential
        Optional<Credential> maybeCred = credentialRepository.findById(userId);
        if (maybeCred.isEmpty()) {
            throw new IllegalArgumentException("Credential not found");
        }

        Credential cred = maybeCred.get();

        if (!cred.hasPassword()) {
            throw new IllegalArgumentException(
                    "No password set for this account. Use password reset to set one.");
        }

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, cred.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Update the password
        String hashedPassword = passwordEncoder.encode(newPassword);
        cred.setPasswordHash(hashedPassword);
        credentialRepository.save(cred);

        // Revoke all refresh tokens for security
        tokenService.revokeAllRefreshTokensForUser(userId);
    }
}
