package com.tomassirio.wanderer.commons.security.revocation;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-based implementation for managing revoked JWT tokens. Tokens are stored with automatic
 * expiration matching their natural JWT expiry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisRevokedTokenCache implements RevokedTokenCache {

    private static final String KEY_PREFIX = "revoked:jti:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void revokeToken(String jti, long expiresInSeconds) {
        String key = KEY_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(expiresInSeconds));
        log.info("Revoked token with JTI: {} (expires in {}s)", jti, expiresInSeconds);
    }

    @Override
    public boolean isTokenRevoked(String jti) {
        String key = KEY_PREFIX + jti;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
