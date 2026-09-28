package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.auth.config.SsoProperties;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import java.time.Duration;
import java.util.List;
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
    private final LoginResponse login =
            new LoginResponse("access", "refresh", "Bearer", 900000L, "ana");

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
    void store_writesJsonWithTtlUnderRandomCode() {
        when(redis.opsForValue()).thenReturn(ops);

        String first = store.store(login);
        String second = store.store(login);

        assertNotEquals(first, second);
        assertTrue(first.length() >= 43, "256-bit url-safe code expected");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(ops)
                .set(
                        eq(SsoLoginCodeStore.KEY_PREFIX + first),
                        json.capture(),
                        eq(Duration.ofSeconds(60)));
        assertTrue(json.getValue().contains("\"accessToken\":\"access\""));
    }

    @Test
    void consume_returnsResponseAndDeletesAtomically() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(SsoLoginCodeStore.KEY_PREFIX + "abc"))
                .thenReturn(
                        "{\"accessToken\":\"access\",\"refreshToken\":\"refresh\","
                                + "\"tokenType\":\"Bearer\",\"expiresIn\":900000,\"username\":\"ana\"}");

        assertEquals(login, store.consume("abc").orElseThrow());
    }

    @Test
    void consume_unknownCode_isEmpty() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(anyString())).thenReturn(null);

        assertTrue(store.consume("nope").isEmpty());
    }
}
