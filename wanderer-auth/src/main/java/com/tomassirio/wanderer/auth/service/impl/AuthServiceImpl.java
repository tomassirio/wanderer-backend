package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.RegisterPendingResponse;
import com.tomassirio.wanderer.auth.dto.RegisterRequest;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.service.AuthService;
import com.tomassirio.wanderer.auth.service.EmailService;
import com.tomassirio.wanderer.auth.service.JwtService;
import com.tomassirio.wanderer.auth.service.TokenService;
import com.tomassirio.wanderer.auth.strategy.UserLookupStrategy;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.security.Role;
import feign.FeignException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final WandererCommandClient wandererCommandClient;
    private final WandererQueryClient wandererQueryClient;
    private final List<UserLookupStrategy> userLookupStrategies;

    /**
     * Verify credentials and return access token and refresh token when valid.
     * Supports login with either username or email.
     *
     * @param identifier username or email address
     * @param password user password
     * @return LoginResponse with tokens
     * @throws IllegalArgumentException when credentials are invalid
     */
    public LoginResponse login(String identifier, String password) {
        // Find the appropriate strategy to lookup the user
        User user = userLookupStrategies.stream()
                .filter(strategy -> strategy.canHandle(identifier))
                .findFirst()
                .flatMap(strategy -> strategy.lookupUser(identifier))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // Find credentials by user id in the auth database
        Optional<Credential> maybeCred = credentialRepository.findById(user.getId());
        if (maybeCred.isEmpty()) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        Credential cred = maybeCred.get();

        if (!cred.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }

        if (!passwordEncoder.matches(password, cred.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Generate tokens
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtService.generateTokenWithJti(user, jti, cred.getRoles());
        String refreshToken = tokenService.createRefreshToken(user.getId());

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

        // Check if email is already in use
        if (credentialRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        // Check if username is already taken by querying the read side
        try {
            User existingUser = wandererQueryClient.getUserByUsername(normalizedUsername);
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
                        request.email(), request.username(), passwordHash);

        // Send verification email with original-cased username
        emailService.sendVerificationEmail(request.email(), request.username(), verificationToken);

        return new RegisterPendingResponse(
                "Registration pending. Please check your email to verify your account.");
    }

    /**
     * Verify email and complete user registration. Validates the verification token, creates the
     * user in the domain, creates credentials, and returns login tokens.
     */
    public LoginResponse verifyEmail(String token) {
        // Validate the verification token and get registration data
        String[] verificationData = tokenService.validateEmailVerificationToken(token);
        String email = verificationData[0];
        String originalUsername = verificationData[1];
        String passwordHash = verificationData[2];

        // Normalize username to lowercase; keep original casing as displayName
        String username = originalUsername.toLowerCase(Locale.ROOT);

        // Double-check that email is still available
        if (credentialRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email already in use: " + email);
        }

        // 1) Create the domain user via the command service (returns UUID)
        var payload = Map.of("username", username, "email", email, "displayName", originalUsername);
        UUID createdUserId;
        try {
            createdUserId = wandererCommandClient.createUser(payload);
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to create user in command service", e);
        }

        // 2) Fetch the created user from query service to get full User object
        User createdUser;
        try {
            createdUser = wandererQueryClient.getUserById(createdUserId);
        } catch (FeignException e) {
            // Attempt to delete the created user since we can't proceed
            try {
                wandererCommandClient.deleteUser(createdUserId);
            } catch (FeignException ex) {
                throw new IllegalStateException(
                        "Failed to fetch created user and failed to rollback: " + ex.getMessage(),
                        e);
            }
            throw new IllegalStateException("Failed to fetch created user from query service", e);
        }

        // 3) Create credential in auth DB — wrap in try/catch and compensate on failure
        try {
            if (credentialRepository.findById(createdUser.getId()).isPresent()) {
                throw new IllegalArgumentException(
                        "Credentials already exist for user: " + createdUser.getId());
            }

            Credential credential =
                    Credential.builder()
                            .userId(createdUser.getId())
                            .passwordHash(passwordHash)
                            .enabled(true)
                            .email(email)
                            .roles(Set.of(Role.USER))
                            .build();
            credentialRepository.save(credential);
        } catch (Exception e) {
            // Attempt to delete the created domain user as compensation
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

        // Mark the verification token as verified
        tokenService.markEmailVerificationTokenAsVerified(token);

        // 4) Issue JWT and refresh token
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtService.generateTokenWithJti(createdUser, jti, Set.of(Role.USER));
        String refreshToken = tokenService.createRefreshToken(createdUser.getId());
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getExpirationMs(),
                createdUser.getUsername());
    }

    @Override
    public void logout(UUID userId) {
        tokenService.revokeAllRefreshTokensForUser(userId);
    }

    @Override
    public String initiatePasswordReset(String email) {
        // Find credential by email
        Optional<Credential> maybeCred = credentialRepository.findByEmail(email);
        if (maybeCred.isEmpty()) {
            throw new IllegalArgumentException("No user found with the provided email");
        }

        Credential cred = maybeCred.get();
        String resetToken = tokenService.createPasswordResetToken(cred.getUserId());

        // Fetch the user to get the username for the email
        String username;
        try {
            User user = wandererQueryClient.getUserById(cred.getUserId());
            username = user.getUsername();
        } catch (FeignException e) {
            // Fall back to email as the greeting name if user lookup fails
            username = email;
        }

        // Send password reset email
        emailService.sendPasswordResetEmail(email, username, resetToken);

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
            User user = wandererQueryClient.getUserById(userId);
            return user.getUsername();
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
