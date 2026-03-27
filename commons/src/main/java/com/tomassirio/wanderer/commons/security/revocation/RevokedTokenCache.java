package com.tomassirio.wanderer.commons.security.revocation;

/** Service for managing revoked JWT tokens using Redis. */
public interface RevokedTokenCache {

    /**
     * Revoke a token by adding its JTI to the blacklist.
     *
     * @param jti the JWT ID
     * @param expiresInSeconds time until the token naturally expires
     */
    void revokeToken(String jti, long expiresInSeconds);

    /**
     * Check if a token is revoked.
     *
     * @param jti the JWT ID
     * @return true if the token is revoked
     */
    boolean isTokenRevoked(String jti);
}
