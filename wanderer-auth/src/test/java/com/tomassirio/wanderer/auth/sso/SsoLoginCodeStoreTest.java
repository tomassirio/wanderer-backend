package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.auth.config.SsoProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SsoLoginCodeStoreTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> ops;

    private SsoLoginCodeStore store;
    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        store =
                new SsoLoginCodeStore(
                        redis,
                        new ObjectMapper(),
                        new SsoProperties(
                                List.of("wanderer://auth/sso-callback"), Duration.ofSeconds(60)));
    }

    @Test
    void store_writesJsonWithTtlUnderRandomCodeAndNoTokens() {
        when(redis.opsForValue()).thenReturn(ops);

        String first = store.store(userId, SsoPkceTest.CHALLENGE);
        String second = store.store(userId, SsoPkceTest.CHALLENGE);

        assertNotEquals(first, second);
        assertTrue(first.length() >= 43, "256-bit url-safe code expected");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(ops)
                .set(
                        eq(SsoLoginCodeStore.KEY_PREFIX + first),
                        json.capture(),
                        eq(Duration.ofSeconds(60)));
        assertTrue(json.getValue().contains("\"userId\":\"" + userId));
        assertTrue(json.getValue().contains("\"codeChallenge\":\"" + SsoPkceTest.CHALLENGE));
        assertFalse(json.getValue().contains("accessToken"));
        assertFalse(json.getValue().contains("refreshToken"));
    }

    @Test
    void consume_withMatchingVerifier_returnsUserIdAndDeletesAtomically() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(SsoLoginCodeStore.KEY_PREFIX + "abc")).thenReturn(STORED);

        assertEquals(userId, store.consume("abc", SsoPkceTest.VERIFIER).orElseThrow());
    }

    @Test
    void consume_withWrongVerifier_isEmptyButStillBurnsTheCode() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(SsoLoginCodeStore.KEY_PREFIX + "abc")).thenReturn(STORED);

        assertTrue(store.consume("abc", "x".repeat(43)).isEmpty());
        verify(ops).getAndDelete(SsoLoginCodeStore.KEY_PREFIX + "abc");
    }

    @Test
    void consume_unknownCode_isEmpty() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(anyString())).thenReturn(null);

        assertTrue(store.consume("nope", SsoPkceTest.VERIFIER).isEmpty());
    }

    private static final String STORED =
            "{\"codeChallenge\":\""
                    + SsoPkceTest.CHALLENGE
                    + "\",\"userId\":\"11111111-1111-1111-1111-111111111111\"}";
}
