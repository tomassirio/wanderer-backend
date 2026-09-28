package com.tomassirio.wanderer.auth.sso;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.auth.config.SsoProperties;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Hands SSO results to the client without putting tokens in a URL: the redirect carries a random
 * single-use code, the client swaps it for the LoginResponse via POST together with the PKCE
 * verifier matching the code_challenge it sent when starting the login.
 */
@Component
@RequiredArgsConstructor
public class SsoLoginCodeStore {

    static final String KEY_PREFIX = "wanderer:auth:sso-code:";
    private static final int CODE_BYTES = 32;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SsoProperties ssoProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** What sits in Redis under a code. Tokens are stored as issued (short TTL, single use). */
    record Entry(String codeChallenge, LoginResponse login) {}

    public String store(String codeChallenge, LoginResponse response) {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue()
                .set(
                        KEY_PREFIX + code,
                        write(new Entry(codeChallenge, response)),
                        ssoProperties.loginCodeTtl());
        return code;
    }

    /** GETDEL first, so a wrong verifier burns the code: every code gets exactly one attempt. */
    public Optional<LoginResponse> consume(String code, String codeVerifier) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(KEY_PREFIX + code))
                .map(this::read)
                .filter(entry -> SsoPkce.matches(entry.codeChallenge(), codeVerifier))
                .map(Entry::login);
    }

    private String write(Entry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SSO login", e);
        }
    }

    private Entry read(String json) {
        try {
            return objectMapper.readValue(json, Entry.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize SSO login", e);
        }
    }
}
