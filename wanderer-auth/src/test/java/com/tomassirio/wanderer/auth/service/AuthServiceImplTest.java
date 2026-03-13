package com.tomassirio.wanderer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.security.Role;
import feign.FeignException;
import feign.FeignException.NotFound;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks private AuthServiceImpl authService;

    private User testUser;

    private Credential testCredential;

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
    }

    @Test
    void login_whenValidCredentials_shouldReturnLoginResponse() {
        String password = "password123";
        String accessToken = "jwt.access.token";
        String refreshToken = "refresh.token";
        long expiresIn = 3600000L;

        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));
        when(passwordEncoder.matches(password, testCredential.getPasswordHash())).thenReturn(true);
        when(jwtService.generateTokenWithJti(any(), any(), any())).thenReturn(accessToken);
        when(tokenService.createRefreshToken(testUser.getId())).thenReturn(refreshToken);
        when(jwtService.getExpirationMs()).thenReturn(expiresIn);

        LoginResponse result = authService.login(testUser.getUsername(), password);

        assertEquals(accessToken, result.accessToken());
        assertEquals(refreshToken, result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(expiresIn, result.expiresIn());
        assertEquals(testUser.getUsername(), result.username());
        verify(jwtService).generateTokenWithJti(any(), any(), any());
        verify(tokenService).createRefreshToken(testUser.getId());
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
                IllegalArgumentException.class, () -> authService.login("nonexistent", "password"));
    }

    @Test
    void login_whenCredentialsNotFound_shouldThrowIllegalArgumentException() {
        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(testUser.getUsername(), "password"));
    }

    @Test
    void login_whenAccountDisabled_shouldThrowIllegalArgumentException() {
        testCredential.setEnabled(false);

        when(wandererQueryClient.getUserByUsername(testUser.getUsername())).thenReturn(testUser);
        when(credentialRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testCredential));

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.login(testUser.getUsername(), "password"));
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
                () -> authService.login(testUser.getUsername(), "wrongpassword"));
    }

    @Test
    void register_whenValidRequest_shouldCreatePendingVerificationAndSendEmail() {
        RegisterRequest request =
                new RegisterRequest("testuser", "test@example.com", "password123");
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
                new RegisterRequest("TestUser", "test@example.com", "password123");
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
                        request.email(), "testuser", "hashedPassword"))
                .thenReturn(verificationToken);

        RegisterPendingResponse result = authService.register(request);

        assertEquals(
                "Registration pending. Please check your email to verify your account.",
                result.message());
        verify(wandererQueryClient).getUserByUsername("testuser");
        verify(tokenService)
                .createEmailVerificationToken(request.email(), "testuser", "hashedPassword");
        verify(emailService).sendVerificationEmail(request.email(), "testuser", verificationToken);
    }

    @Test
    void register_whenEmailAlreadyExists_shouldThrowException() {
        RegisterRequest request =
                new RegisterRequest("testuser", "existing@example.com", "password123");

        when(credentialRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(testCredential));

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(tokenService, never()).createEmailVerificationToken(any(), any(), any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void register_whenUsernameAlreadyExists_shouldThrowException() {
        RegisterRequest request =
                new RegisterRequest("existinguser", "test@example.com", "password123");

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
    void logout_shouldRevokeRefreshTokens() {
        authService.logout(testUser.getId());

        verify(tokenService).revokeAllRefreshTokensForUser(testUser.getId());
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
        String newPassword = "newPassword123";
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
        String newPassword = "newPassword123";
        UUID userId = UUID.randomUUID();

        when(tokenService.validatePasswordResetToken(token)).thenReturn(userId);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class, () -> authService.resetPassword(token, newPassword));
    }

    @Test
    void changePassword_whenValidCurrentPassword_shouldUpdatePassword() {
        String currentPassword = "currentPassword";
        String newPassword = "newPassword123";
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
        String newPassword = "newPassword123";
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
        String newPassword = "newPassword123";
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
                        () -> authService.login("testuser", "password"));

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
                        () -> authService.login("testuser", "password"));

        assertEquals("Failed to contact user query service", exception.getMessage());
        assertEquals(serverError, exception.getCause());
        verify(credentialRepository, never()).findById(any());
    }

    @Test
    void login_whenMixedCaseUsername_shouldNormalizeToLowercase() {
        String password = "password123";
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
        LoginResponse result = authService.login("TestUser", password);

        assertEquals(accessToken, result.accessToken());
        verify(wandererQueryClient).getUserByUsername("testuser");
    }
}
