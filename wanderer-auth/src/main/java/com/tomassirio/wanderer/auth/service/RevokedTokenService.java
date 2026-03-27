package com.tomassirio.wanderer.auth.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for managing revoked access tokens (JTI blacklist).
 */
public interface RevokedTokenService {

    /**
     * Revoke an access token by its JTI.
     *
     * @param jti the JWT ID
     * @param userId the user ID
     * @param expiresAt when the token expires
     */
    void revokeToken(String jti, UUID userId, Instant expiresAt);

    /**
     * Check if a token is revoked.
     *
     * @param jti the JWT ID
     * @return true if the token is revoked
     */
    boolean isTokenRevoked(String jti);

    /**
     * Clean up expired revoked tokens.
     * Should be called periodically by a scheduled task.
     *
     * @return number of tokens removed
     */
    int cleanupExpiredTokens();
}
