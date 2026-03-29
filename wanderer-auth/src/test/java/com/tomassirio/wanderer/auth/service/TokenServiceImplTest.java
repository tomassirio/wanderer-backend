package com.tomassirio.wanderer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.PasswordResetToken;
import com.tomassirio.wanderer.auth.domain.RefreshToken;
import com.tomassirio.wanderer.auth.dto.RefreshTokenResponse;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.repository.PasswordResetTokenRepository;
import com.tomassirio.wanderer.auth.repository.RefreshTokenRepository;
import com.tomassirio.wanderer.auth.service.impl.TokenServiceImpl;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import com.tomassirio.wanderer.commons.security.Role;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock private CredentialRepository credentialRepository;

    @Mock private JwtService jwtService;

    @Mock private WandererQueryClient wandererQueryClient;

    @InjectMocks private TokenServiceImpl tokenService;

    private UUID testUserId;
    private UserBasicInfo testUserInfo;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUserInfo = new UserBasicInfo(testUserId, "testuser");
    }

    @Test
    void createRefreshToken_shouldCreateAndReturnToken() {
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L); // 7 days

        String token = tokenService.createRefreshToken(testUserId);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void refreshAccessToken_whenValidToken_shouldReturnNewTokens() {
        // Given
        String refreshToken = "validRefreshToken";
        String tokenHash = "hashedToken";
        RefreshToken storedToken =
                RefreshToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash(tokenHash)
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .revoked(false)
                        .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(storedToken));
        when(wandererQueryClient.getUserById(testUserId, "basic")).thenReturn(testUserInfo);

        Credential testCredential =
                Credential.builder().userId(testUserId).roles(Set.of(Role.USER)).build();
        when(credentialRepository.findById(testUserId)).thenReturn(Optional.of(testCredential));

        when(jwtService.generateTokenWithJti(any(), anyString(), any()))
                .thenReturn("newAccessToken");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        // When
        RefreshTokenResponse response = tokenService.refreshAccessToken(refreshToken);

        // Then
        assertNotNull(response);
        assertEquals("newAccessToken", response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class)); // old revoked + new
    }

    @Test
    void refreshAccessToken_whenTokenNotFound_shouldThrowException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenService.refreshAccessToken("invalidToken"));
    }

    @Test
    void refreshAccessToken_whenTokenRevoked_shouldThrowException() {
        RefreshToken revokedToken =
                RefreshToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash("hashedToken")
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .revoked(true)
                        .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(revokedToken));

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenService.refreshAccessToken("revokedToken"));
    }

    @Test
    void refreshAccessToken_whenTokenExpired_shouldThrowException() {
        RefreshToken expiredToken =
                RefreshToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash("hashedToken")
                        .expiresAt(Instant.now().minusSeconds(3600))
                        .revoked(false)
                        .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(expiredToken));

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenService.refreshAccessToken("expiredToken"));
    }

    @Test
    void createPasswordResetToken_shouldCreateAndReturnToken() {
        String token = tokenService.createPasswordResetToken(testUserId);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
    }

    @Test
    void validatePasswordResetToken_whenValid_shouldReturnUserId() {
        String token = "validResetToken";
        PasswordResetToken resetToken =
                PasswordResetToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash("hashedToken")
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .used(false)
                        .build();

        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(resetToken));

        UUID userId = tokenService.validatePasswordResetToken(token);

        assertEquals(testUserId, userId);
    }

    @Test
    void validatePasswordResetToken_whenNotFound_shouldThrowException() {
        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenService.validatePasswordResetToken("invalidToken"));
    }

    @Test
    void validatePasswordResetToken_whenUsed_shouldThrowException() {
        PasswordResetToken usedToken =
                PasswordResetToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash("hashedToken")
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .used(true)
                        .build();

        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(usedToken));

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenService.validatePasswordResetToken("usedToken"));
    }

    @Test
    void validatePasswordResetToken_whenExpired_shouldThrowException() {
        PasswordResetToken expiredToken =
                PasswordResetToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash("hashedToken")
                        .expiresAt(Instant.now().minusSeconds(3600))
                        .used(false)
                        .build();

        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(expiredToken));

        assertThrows(
                IllegalArgumentException.class,
                () -> tokenService.validatePasswordResetToken("expiredToken"));
    }

    @Test
    void markPasswordResetTokenAsUsed_whenTokenExists_shouldMarkAsUsed() {
        // Given
        String token = "validResetToken";
        PasswordResetToken resetToken =
                PasswordResetToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash("hashedToken")
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .used(false)
                        .build();

        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(resetToken));

        // When
        tokenService.markPasswordResetTokenAsUsed(token);

        // Then
        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isUsed());
    }

    @Test
    void markPasswordResetTokenAsUsed_whenTokenNotFound_shouldNotThrowException() {
        // Given
        when(passwordResetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        // When/Then - should not throw exception
        tokenService.markPasswordResetTokenAsUsed("nonExistentToken");

        // Verify save was never called
        verify(passwordResetTokenRepository, times(0)).save(any(PasswordResetToken.class));
    }

    @Test
    void revokeAllRefreshTokensForUser_shouldDeleteAllTokens() {
        // When
        tokenService.revokeAllRefreshTokensForUser(testUserId);

        // Then
        verify(refreshTokenRepository, times(1)).deleteAllByUserId(testUserId);
    }

    @Test
    void refreshAccessToken_whenUserFetchFails_shouldThrowIllegalStateException() {
        // Given
        String refreshToken = "validRefreshToken";
        RefreshToken storedToken =
                RefreshToken.builder()
                        .tokenId(UUID.randomUUID())
                        .userId(testUserId)
                        .tokenHash("hashedToken")
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .revoked(false)
                        .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(storedToken));
        when(wandererQueryClient.getUserById(testUserId, "basic"))
                .thenThrow(feign.FeignException.NotFound.class);

        // When/Then
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> tokenService.refreshAccessToken(refreshToken));

        assertEquals("Failed to fetch user information", exception.getMessage());
        assertInstanceOf(feign.FeignException.class, exception.getCause());
    }

    @Test
    void createRefreshToken_whenSHA256Unavailable_shouldThrowIllegalStateException() {
        // Given - Mock MessageDigest to throw NoSuchAlgorithmException

        try (MockedStatic<MessageDigest> mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest
                    .when(() -> MessageDigest.getInstance(eq("SHA-256")))
                    .thenThrow(new NoSuchAlgorithmException("SHA-256 not available"));

            // When/Then - Should throw IllegalStateException wrapping NoSuchAlgorithmException
            IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            () -> tokenService.createRefreshToken(testUserId));

            assertEquals("SHA-256 algorithm not available", exception.getMessage());
            assertInstanceOf(NoSuchAlgorithmException.class, exception.getCause());
        }
    }

    @Test
    void createPasswordResetToken_whenSHA256Unavailable_shouldThrowIllegalStateException() {
        // Given - Mock MessageDigest to throw NoSuchAlgorithmException
        try (MockedStatic<MessageDigest> mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest
                    .when(() -> MessageDigest.getInstance(eq("SHA-256")))
                    .thenThrow(new NoSuchAlgorithmException("SHA-256 not available"));

            // When/Then - Should throw IllegalStateException wrapping NoSuchAlgorithmException
            IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            () -> tokenService.createPasswordResetToken(testUserId));

            assertEquals("SHA-256 algorithm not available", exception.getMessage());
            assertInstanceOf(NoSuchAlgorithmException.class, exception.getCause());
        }
    }
}
