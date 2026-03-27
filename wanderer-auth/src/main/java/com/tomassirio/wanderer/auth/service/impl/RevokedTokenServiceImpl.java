package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.domain.RevokedAccessToken;
import com.tomassirio.wanderer.auth.repository.RevokedAccessTokenRepository;
import com.tomassirio.wanderer.auth.service.RevokedTokenService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service implementation for managing revoked access tokens. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevokedTokenServiceImpl implements RevokedTokenService {

    private final RevokedAccessTokenRepository revokedAccessTokenRepository;

    @Override
    @Transactional
    public void revokeToken(String jti, UUID userId, Instant expiresAt) {
        RevokedAccessToken revokedToken =
                RevokedAccessToken.builder().jti(jti).userId(userId).expiresAt(expiresAt).build();

        revokedAccessTokenRepository.save(revokedToken);
        log.debug("Revoked access token with JTI: {} for user: {}", jti, userId);
    }

    @Override
    public boolean isTokenRevoked(String jti) {
        return revokedAccessTokenRepository.existsByJti(jti);
    }

    @Override
    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = revokedAccessTokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired revoked tokens", deleted);
        }
        return deleted;
    }
}
