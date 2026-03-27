package com.tomassirio.wanderer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.RegisterPendingResponse;
import com.tomassirio.wanderer.auth.dto.RegisterRequest;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.service.impl.AuthServiceImpl;
import com.tomassirio.wanderer.auth.strategy.EmailLookupStrategy;
import com.tomassirio.wanderer.auth.strategy.UserLookupStrategy;
import com.tomassirio.wanderer.auth.strategy.UsernameLookupStrategy;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.security.Role;
import com.tomassirio.wanderer.commons.security.revocation.RevokedTokenCache;
import feign.FeignException;
import feign.FeignException.NotFound;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private CredentialRepository credentialRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private JwtService jwtService;

    @Mock private TokenService tokenService;

    @Mock private EmailService emailService;

    @Mock private WandererCommandClient wandererCommandClient;

    @Mock private WandererQueryClient wandererQueryClient;

    @Mock private RevokedTokenCache revokedTokenCache;

    @Mock private LoginAttemptService loginAttemptService;

    private AuthServiceImpl authService;

    private User testUser;

    private Credential testCredential;

    private List<UserLookupStrategy> strategies;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(UUID.randomUUID()).username("testuser").build();
        testCredential =
                Credential.builder()
                        .userId(testUser.getId())
                        .passwordHash("hashedPassword")
                        .enabled(true)
                        .email("user@email.com")
                        .roles(Set.of(Role.USER))
                        .build();

        // Create real strategy instances for testing
        strategies =
                List.of(
                        new EmailLookupStrategy(credentialRepository, wandererQueryClient),
                        new UsernameLookupStrategy(wandererQueryClient));

        authService =
                new AuthServiceImpl(
                        credentialRepository,
                        passwordEncoder,
                        jwtService,
                        tokenService,
                        emailService,
                        wandererCommandClient,
                        wandererQueryClient,
                        strategies,
                        revokedTokenCache,
                        loginAttemptService);
    }

    @Test
    void login_whenValidCredentials_shouldReturnLoginResponse() {
        String password = "SecurePass1!";
        String accessToken = "jwt.access.token";
        String refreshToken = "refresh.token";
        long expiresIn = 3600000L;
        String ipAddress = "192.168.1.1";

        when(loginAttemptService.isAccountLocked(testUser.getUsername())).thenReturn(false);
        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches(password, testCredential.getPasswordHash())).thenReturn(true);
        when(jwtService.generateTokenWithJti(any(), any(), any())).thenReturn(accessToken);
        when(tokenService.createRefreshToken(testUser.getId())).thenReturn(refreshToken);
        when(jwtService.getExpirationMs()).thenReturn(expiresIn);

        LoginResponse result = authService.login(testUser.getUsername(), password, ipAddress);

        assertEquals(accessToken, result.accessToken());
        assertEquals(refreshToken, result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(expiresIn, result.expiresIn());
        assertEquals(testUser.getUsername(), result.username());
        verify(jwtService).generateTokenWithJti(any(), any(), any());
        verify(tokenService).createRefreshToken(testUser.getId());
        verify(loginAttemptService)
                .recordSuccessfulLogin(testUser.getUsername(), testUser.getId(), ipAddress);
    }

    @Test
    void login_whenUserNotFound_shouldThrowIllegalArgumentException() {
        Request dummyRequest =
                Request.create(
                        Request.HttpMethod.GET,
                        "http://dummy",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);
        when(wandererQueryClient.getUserByUsername("nonexistent"))
                .thenThrow(new NotFound("User not found", dummyRequest, null, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login("nonexistent", "password", "127.0.0.1"));
    }

    @Test
    void login_whenCredentialsNotFound_shouldThrowIllegalArgumentException() {
        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(testUser.getUsername(), "password", "127.0.0.1"));
    }

    @Test
    void login_whenAccountDisabled_shouldThrowIllegalArgumentException() {
        testCredential.setEnabled(false);

        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(testUser.getUsername(), "password", "127.0.0.1"));
    }

    @Test
    void login_whenPasswordIncorrect_shouldThrowIllegalArgumentException() {
        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches("wrongpassword", testCredential.getPasswordHash()))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(testUser.getUsername(), "wrongpassword", "127.0.0.1"));
    }

    @Test
    void login_whenValidEmailProvided_shouldReturnLoginResponse() {
        String email = "user@email.com";
        String password = "SecurePass1!";
        String accessToken = "jwt.access.token";
        String refreshToken = "refresh.token";
        long expiresIn = 3600000L;

        when(credentialRepository.findByEmail(email)).thenReturn(Optional.of(testCredential));
        when(wandererQueryClient.getUserById(testUser.getId())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches(password, testCredential.getPasswordHash())).thenReturn(true);
        when(jwtService.generateTokenWithJti(any(), any(), any())).thenReturn(accessToken);
        when(tokenService.createRefreshToken(testUser.getId())).thenReturn(refreshToken);
        when(jwtService.getExpirationMs()).thenReturn(expiresIn);

        LoginResponse result = authService.login(email, password, "127.0.0.1");

        assertEquals(accessToken, result.accessToken());
        assertEquals(refreshToken, result.refreshToken());
        assertEquals(testUser.getUsername(), result.username());
        verify(credentialRepository).findByEmail(email);
        verify(wandererQueryClient).getUserById(testUser.getId());
    }

    @Test
    void login_whenEmailNotFound_shouldThrowIllegalArgumentException() {
        String email = "notfound@example.com";
        when(credentialRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(email, "password", "127.0.0.1"));
    }

    @Test
    void login_whenAccountIsLocked_shouldThrowIllegalArgumentException() {
        String identifier = "testuser";
        String ipAddress = "192.168.1.1";

        when(loginAttemptService.isAccountLocked(identifier)).thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login(identifier, "password", ipAddress));

        assertTrue(exception.getMessage().contains("Account temporarily locked"));
        verify(loginAttemptService).recordFailedLogin(identifier, ipAddress);
        verify(wandererQueryClient, never()).getUserByUsername(any());
    }

    @Test
    void login_whenPasswordIncorrect_shouldRecordFailedAttempt() {
        String ipAddress = "192.168.1.1";

        when(loginAttemptService.isAccountLocked(testUser.getUsername())).thenReturn(false);
        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches("wrongpassword", testCredential.getPasswordHash()))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(testUser.getUsername(), "wrongpassword", ipAddress));

        verify(loginAttemptService).recordFailedLogin(testUser.getUsername(), ipAddress);
    }

    @Test
    void register_whenValidRequest_shouldCreatePendingVerificationAndSendEmail() {
        RegisterRequest request =
                new RegisterRequest("testuser", "test@example.com", "SecurePass1!");
        String verificationToken = "verification.token";
        Request dummyRequest =
                Request.create(
                        Request.HttpMethod.GET,
                        "http://dummy",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);

        when(credentialRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(wandererQueryClient.getUserByUsername("testuser"))
                .thenThrow(
                        new NotFound("User not found", dummyRequest, null, null)); // 404 expected
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(tokenService.createEmailVerificationToken(
                        request.email(), "testuser", "hashedPassword"))
                .thenReturn(verificationToken);

        RegisterPendingResponse result = authService.register(request);

        assertEquals(
                "Registration pending. Please check your email to verify your account.",
                result.message());
        verify(emailService).sendVerificationEmail(request.email(), "testuser", verificationToken);
        verify(wandererCommandClient, never()).createUser(any());
    }

    @Test
    void register_whenMixedCaseUsername_shouldNormalizeToLowercase() {
        RegisterRequest request =
                new RegisterRequest("TestUser", "test@example.com", "SecurePass1!");
        String verificationToken = "verification.token";
        Request dummyRequest =
                Request.create(
                        Request.HttpMethod.GET,
                        "http://dummy",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);

        when(credentialRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(wandererQueryClient.getUserByUsername("testuser"))
                .thenThrow(new NotFound("User not found", dummyRequest, null, null));
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(tokenService.createEmailVerificationToken(
                        request.email(), "TestUser", "hashedPassword"))
                .thenReturn(verificationToken);

        RegisterPendingResponse result = authService.register(request);

        assertEquals(
                "Registration pending. Please check your email to verify your account.",
                result.message());
        // Uniqueness check should use lowercase
        verify(wandererQueryClient).getUserByUsername("testuser");
        // Token and email should preserve original casing for displayName
        verify(tokenService)
                .createEmailVerificationToken(request.email(), "TestUser", "hashedPassword");
        verify(emailService).sendVerificationEmail(request.email(), "TestUser", verificationToken);
    }

    @Test
    void register_whenEmailAlreadyExists_shouldThrowException() {
        RegisterRequest request =
                new RegisterRequest("testuser", "existing@example.com", "SecurePass1!");

        when(credentialRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(testCredential));

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(tokenService, never()).createEmailVerificationToken(any(), any(), any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void register_whenUsernameAlreadyExists_shouldThrowException() {
        RegisterRequest request =
                new RegisterRequest("existinguser", "test@example.com", "SecurePass1!");

        when(credentialRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(wandererQueryClient.getUserByUsername("existinguser")).thenReturn(testUser);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(tokenService, never()).createEmailVerificationToken(any(), any(), any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void verifyEmail_whenValidToken_shouldCreateUserAndReturnLoginResponse() {
        String verificationToken = "verification.token";
        String[] verificationData = new String[] {"test@example.com", "testuser", "hashedPassword"};
        String accessToken = "jwt.access.token";
        String refreshToken = "refresh.token";
        long expiresIn = 3600000L;

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail(verificationData[0])).thenReturn(Optional.empty());
        when(wandererCommandClient.createUser(any())).thenReturn(testUser.getId());
        when(wandererQueryClient.getUserById(testUser.getId())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId())).thenReturn(Optional.empty());
        when(jwtService.generateTokenWithJti(any(), any(), any())).thenReturn(accessToken);
        when(tokenService.createRefreshToken(testUser.getId())).thenReturn(refreshToken);
        when(jwtService.getExpirationMs()).thenReturn(expiresIn);

        LoginResponse result = authService.verifyEmail(verificationToken);

        assertEquals(accessToken, result.accessToken());
        assertEquals(refreshToken, result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(expiresIn, result.expiresIn());
        assertEquals(testUser.getUsername(), result.username());
        verify(credentialRepository).save(any(Credential.class));
        verify(tokenService).markEmailVerificationTokenAsVerified(verificationToken);
        verify(wandererCommandClient, never()).deleteUser(any());
    }

    @Test
    void verifyEmail_whenMixedCaseUsername_shouldPassDisplayNameToCommandService() {
        String verificationToken = "verification.token";
        // Token stores the original-cased username from registration
        String[] verificationData = new String[] {"test@example.com", "TestUser", "hashedPassword"};
        String accessToken = "jwt.access.token";
        String refreshToken = "refresh.token";
        long expiresIn = 3600000L;

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail(verificationData[0])).thenReturn(Optional.empty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        when(wandererCommandClient.createUser(payloadCaptor.capture()))
                .thenReturn(testUser.getId());
        when(wandererQueryClient.getUserById(testUser.getId())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId())).thenReturn(Optional.empty());
        when(jwtService.generateTokenWithJti(any(), any(), any())).thenReturn(accessToken);
        when(tokenService.createRefreshToken(testUser.getId())).thenReturn(refreshToken);
        when(jwtService.getExpirationMs()).thenReturn(expiresIn);

        authService.verifyEmail(verificationToken);

        // Verify the payload sent to the command service
        Map<String, String> payload = payloadCaptor.getValue();
        assertEquals("testuser", payload.get("username"));
        assertEquals("TestUser", payload.get("displayName"));
    }

    @Test
    void verifyEmail_whenEmailAlreadyInUse_shouldThrowException() {
        String verificationToken = "verification.token";
        String[] verificationData =
                new String[] {"existing@example.com", "testuser", "hashedPassword"};

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail(verificationData[0]))
                .thenReturn(Optional.of(testCredential));

        assertThrows(IllegalStateException.class, () -> authService.verifyEmail(verificationToken));
        verify(wandererCommandClient, never()).createUser(any());
    }

    @Test
    void verifyEmail_whenUserCreationFails_shouldThrowIllegalStateException() {
        String verificationToken = "verification.token";
        String[] verificationData = new String[] {"test@example.com", "testuser", "hashedPassword"};

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail(verificationData[0])).thenReturn(Optional.empty());
        when(wandererCommandClient.createUser(any())).thenThrow(FeignException.class);

        assertThrows(IllegalStateException.class, () -> authService.verifyEmail(verificationToken));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void verifyEmail_whenFetchUserFails_shouldRollbackAndThrowIllegalStateException() {
        String verificationToken = "verification.token";
        String[] verificationData = new String[] {"test@example.com", "testuser", "hashedPassword"};

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail(verificationData[0])).thenReturn(Optional.empty());
        when(wandererCommandClient.createUser(any())).thenReturn(testUser.getId());
        when(wandererQueryClient.getUserById(testUser.getId())).thenThrow(FeignException.class);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> authService.verifyEmail(verificationToken));

        assertEquals("Failed to fetch created user from query service", exception.getMessage());
        verify(wandererCommandClient).deleteUser(testUser.getId());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void logout_shouldRevokeRefreshTokensAndAccessToken() {
        String jti = "test-jti-123";
        Instant expiresAt = Instant.now().plusSeconds(3600);

        authService.logout(testUser.getId(), jti, expiresAt);

        verify(tokenService).revokeAllRefreshTokensForUser(testUser.getId());
        // Verify JTI is revoked with approximately 3600 seconds (allow for timing variance)
        verify(revokedTokenCache).revokeToken(eq(jti), anyLong());
    }

    @Test
    void initiatePasswordReset_whenEmailExists_shouldReturnResetToken() {
        String email = "user@email.com";
        String resetToken = "reset.token";

        when(credentialRepository.findByEmail(email)).thenReturn(Optional.of(testCredential));
        when(tokenService.createPasswordResetToken(testCredential.getUserId()))
                .thenReturn(resetToken);
        when(wandererQueryClient.getUserById(testCredential.getUserId())).thenReturn(testUser);

        String result = authService.initiatePasswordReset(email);

        assertEquals(resetToken, result);
        verify(tokenService).createPasswordResetToken(testCredential.getUserId());
        verify(emailService).sendPasswordResetEmail(email, testUser.getUsername(), resetToken);
    }

    @Test
    void initiatePasswordReset_whenUserLookupFails_shouldFallBackToEmailAsUsername() {
        String email = "user@email.com";
        String resetToken = "reset.token";

        when(credentialRepository.findByEmail(email)).thenReturn(Optional.of(testCredential));
        when(tokenService.createPasswordResetToken(testCredential.getUserId()))
                .thenReturn(resetToken);
        Request dummyRequest =
                Request.create(
                        Request.HttpMethod.GET,
                        "/test",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);
        when(wandererQueryClient.getUserById(testCredential.getUserId()))
                .thenThrow(new NotFound("Not Found", dummyRequest, null, Map.of()));

        String result = authService.initiatePasswordReset(email);

        assertEquals(resetToken, result);
        verify(emailService).sendPasswordResetEmail(email, email, resetToken);
    }

    @Test
    void initiatePasswordReset_whenEmailNotFound_shouldThrowException() {
        String email = "nonexistent@email.com";

        when(credentialRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class, () -> authService.initiatePasswordReset(email));
    }

    @Test
    void resetPassword_whenValidToken_shouldUpdatePassword() {
        String token = "reset.token";
        String newPassword = "NewPass123!";
        UUID userId = testUser.getId();

        when(tokenService.validatePasswordResetToken(token)).thenReturn(userId);
        when(credentialRepository.findById(userId)).thenReturn(Optional.of(testCredential));
        when(passwordEncoder.encode(newPassword)).thenReturn("hashedNewPassword");
        when(wandererQueryClient.getUserById(userId)).thenReturn(testUser);

        String username = authService.resetPassword(token, newPassword);

        assertEquals(testUser.getUsername(), username);
        verify(credentialRepository).save(testCredential);
        verify(tokenService).markPasswordResetTokenAsUsed(token);
        verify(tokenService).revokeAllRefreshTokensForUser(userId);
    }

    @Test
    void resetPassword_whenCredentialNotFound_shouldThrowException() {
        String token = "reset.token";
        String newPassword = "NewPass123!";
        UUID userId = UUID.randomUUID();

        when(tokenService.validatePasswordResetToken(token)).thenReturn(userId);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class, () -> authService.resetPassword(token, newPassword));
    }

    @Test
    void changePassword_whenValidCurrentPassword_shouldUpdatePassword() {
        String currentPassword = "currentPassword";
        String newPassword = "NewPass123!";
        UUID userId = testUser.getId();

        when(credentialRepository.findById(userId)).thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches(currentPassword, testCredential.getPasswordHash()))
                .thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("hashedNewPassword");

        authService.changePassword(userId, currentPassword, newPassword);

        verify(credentialRepository).save(testCredential);
        verify(tokenService).revokeAllRefreshTokensForUser(userId);
    }

    @Test
    void changePassword_whenInvalidCurrentPassword_shouldThrowException() {
        String currentPassword = "wrongPassword";
        String newPassword = "NewPass123!";
        UUID userId = testUser.getId();

        when(credentialRepository.findById(userId)).thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches(currentPassword, testCredential.getPasswordHash()))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.changePassword(userId, currentPassword, newPassword));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void changePassword_whenCredentialNotFound_shouldThrowException() {
        String currentPassword = "currentPassword";
        String newPassword = "NewPass123!";
        UUID userId = UUID.randomUUID();

        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.changePassword(userId, currentPassword, newPassword));
    }

    @Test
    void login_whenUserReturnsNull_shouldThrowIllegalArgumentException() {
        // Test the null check after successful FeignClient call
        when(wandererQueryClient.getUserByUsername("testuser")).thenReturn(null);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login("testuser", "password", "127.0.0.1"));

        assertEquals("Invalid credentials", exception.getMessage());
        verify(credentialRepository, never()).findById(any());
    }

    @Test
    void login_whenFeignExceptionNon404_shouldThrowIllegalStateException() {
        // Test FeignException with status code other than 404 (e.g., 500, 503)
        Request dummyRequest =
                Request.create(
                        Request.HttpMethod.GET,
                        "http://dummy",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);
        FeignException.InternalServerError serverError =
                new FeignException.InternalServerError(
                        "Internal Server Error", dummyRequest, null, null);

        when(wandererQueryClient.getUserByUsername("testuser")).thenThrow(serverError);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> authService.login("testuser", "password", "127.0.0.1"));

        assertEquals("Failed to contact user query service", exception.getMessage());
        assertEquals(serverError, exception.getCause());
        verify(credentialRepository, never()).findById(any());
    }

    @Test
    void login_whenMixedCaseUsername_shouldNormalizeToLowercase() {
        String password = "SecurePass1!";
        String accessToken = "jwt.access.token";
        String refreshToken = "refresh.token";
        long expiresIn = 3600000L;

        when(wandererQueryClient.getUserByUsername("testuser")).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches(password, testCredential.getPasswordHash())).thenReturn(true);
        when(jwtService.generateTokenWithJti(any(), any(), any())).thenReturn(accessToken);
        when(tokenService.createRefreshToken(testUser.getId())).thenReturn(refreshToken);
        when(jwtService.getExpirationMs()).thenReturn(expiresIn);

        // Login with mixed case "TestUser" — should be normalized to "testuser"
        LoginResponse result = authService.login("TestUser", password, "127.0.0.1");

        assertEquals(accessToken, result.accessToken());
        verify(wandererQueryClient).getUserByUsername("testuser");
    }
}
