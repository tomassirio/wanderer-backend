package com.tomassirio.wanderer.auth.sso;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.auth.AuthApplication;
import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@Import(TestConfig.class)
@TestPropertySource(
        properties =
                "jwt.secret=test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum")
class SsoLoginFlowIT extends BaseIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (!redis.isRunning()) {
            redis.start();
        }
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean private WandererCommandClient wandererCommandClient;
    @MockitoBean private WandererQueryClient wandererQueryClient;

    @Autowired private MockMvc mockMvc;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private SsoLoginCodeStore codeStore;

    @Test
    void authorization_redirectsToGoogleWithPkceAndStoresSessionInRedis() throws Exception {
        mockMvc.perform(
                        get("/api/1/auth/oauth2/authorization/google")
                                .param("return_to", "wanderer://auth/sso-callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/")))
                .andExpect(header().string("Location", containsString("code_challenge=")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(
                        header().string(
                                        "Location",
                                        containsString(
                                                "redirect_uri=http://localhost/api/1/auth/oauth2/callback/google")))
                .andExpect(header().string("Set-Cookie", containsString("WANDERER_SSO_SESSION=")));

        assertFalse(redisTemplate.keys("wanderer:auth:session:sessions:*").isEmpty());
    }

    @Test
    void callback_withoutHandshakeSession_redirectsToDefaultWithError() throws Exception {
        mockMvc.perform(
                        get("/api/1/auth/oauth2/callback/google")
                                .param("code", "fake")
                                .param("state", "fake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        header().string(
                                        "Location",
                                        "http://localhost:3000/auth/sso-callback?error=sso_failed"));
    }

    @Test
    void exchange_isSingleUseAndApiStaysSessionless() throws Exception {
        String code = codeStore.store(new LoginResponse("access", "refresh", "Bearer", 1L, "ana"));
        String body = "{\"code\":\"" + code + "\"}";

        mockMvc.perform(
                        post("/api/1/auth/sso/exchange")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        mockMvc.perform(
                        post("/api/1/auth/sso/exchange")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }
}
