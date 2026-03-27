package com.tomassirio.wanderer.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tomassirio.wanderer.auth.domain.RevokedAccessToken;
import com.tomassirio.wanderer.auth.repository.RevokedAccessTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokedTokenServiceImplTest {

    @Mock private RevokedAccessTokenRepository revokedAccessTokenRepository;

    @InjectMocks private RevokedTokenServiceImpl revokedTokenService;

    private UUID userId;
    private String jti;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jti = "test-jti-12345";
        expiresAt = Instant.now().plusSeconds(3600);
    }

    @Test
    void revokeToken_shouldSaveRevokedToken() {
        revokedTokenService.revokeToken(jti, userId, expiresAt);

        ArgumentCaptor<RevokedAccessToken> captor =
                ArgumentCaptor.forClass(RevokedAccessToken.class);
        verify(revokedAccessTokenRepository).save(captor.capture());

        RevokedAccessToken saved = captor.getValue();
        assertEquals(jti, saved.getJti());
        assertEquals(userId, saved.getUserId());
        assertEquals(expiresAt, saved.getExpiresAt());
    }

    @Test
    void isTokenRevoked_whenTokenExists_shouldReturnTrue() {
        when(revokedAccessTokenRepository.existsByJti(jti)).thenReturn(true);

        boolean result = revokedTokenService.isTokenRevoked(jti);

        assertTrue(result);
        verify(revokedAccessTokenRepository).existsByJti(jti);
    }

    @Test
    void isTokenRevoked_whenTokenDoesNotExist_shouldReturnFalse() {
        when(revokedAccessTokenRepository.existsByJti(jti)).thenReturn(false);

        boolean result = revokedTokenService.isTokenRevoked(jti);

        assertFalse(result);
        verify(revokedAccessTokenRepository).existsByJti(jti);
    }

    @Test
    void cleanupExpiredTokens_shouldDeleteExpiredTokens() {
        int deletedCount = 5;
        when(revokedAccessTokenRepository.deleteExpiredTokens(any(Instant.class)))
                .thenReturn(deletedCount);

        int result = revokedTokenService.cleanupExpiredTokens();

        assertEquals(deletedCount, result);
        verify(revokedAccessTokenRepository).deleteExpiredTokens(any(Instant.class));
    }

    @Test
    void cleanupExpiredTokens_whenNoTokensToDelete_shouldReturnZero() {
        when(revokedAccessTokenRepository.deleteExpiredTokens(any(Instant.class))).thenReturn(0);

        int result = revokedTokenService.cleanupExpiredTokens();

        assertEquals(0, result);
        verify(revokedAccessTokenRepository).deleteExpiredTokens(any(Instant.class));
    }
}
