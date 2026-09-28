package com.tomassirio.wanderer.auth.sso;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.auth.AuthApplication;
import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import jakarta.servlet.Filter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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

    // RFC 7636 Appendix B
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

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
    @Autowired private FilterChainProxy filterChainProxy;
    @Autowired private CredentialRepository credentialRepository;

    @Test
    void authorization_redirectsToGoogleWithPkceAndStoresSessionInRedis() throws Exception {
        mockMvc.perform(
                        get("/api/1/auth/oauth2/authorization/google")
                                .param("return_to", "wanderer://auth/sso-callback")
                                .param("code_challenge", CHALLENGE)
                                .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/")))
                .andExpect(header().string("Location", containsString("code_challenge=")))
                // Google receives Spring's own PKCE challenge, not the client's
                .andExpect(header().string("Location", not(containsString(CHALLENGE))))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(
                        header().string(
                                        "Location",
                                        containsString(
                                                "redirect_uri=http://localhost/api/1/auth/oauth2/callback/google")))
                .andExpect(header().string("Set-Cookie", containsString("WANDERER_SSO_SESSION=")))
                // Covers /api/1/auth/oauth2/callback/google, nothing else on the API host
                .andExpect(
                        header().string("Set-Cookie", containsString("Path=/api/1/auth/oauth2")));

        assertFalse(redisTemplate.keys("wanderer:auth:session:sessions:*").isEmpty());
    }

    @Test
    void authorization_withoutCodeChallenge_stillRedirectsToGoogle() throws Exception {
        // Rejected after the callback (success handler) so the user lands on return_to?error=...
        mockMvc.perform(
                        get("/api/1/auth/oauth2/authorization/google")
                                .param("return_to", "wanderer://auth/sso-callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/")));
    }

    @Test
    void ssoFilterChain_authorizedClientRepositoryIsSessionScopedNotInMemory() throws Exception {
        MockHttpServletRequest authorizationRequest =
                new MockHttpServletRequest("GET", "/api/1/auth/oauth2/authorization/google");

        SecurityFilterChain ssoChain =
                filterChainProxy.getFilterChains().stream()
                        .filter(chain -> chain.matches(authorizationRequest))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No chain matches the SSO path"));

        List<Filter> filters = ssoChain.getFilters();
        OAuth2LoginAuthenticationFilter loginFilter =
                filters.stream()
                        .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                        .map(OAuth2LoginAuthenticationFilter.class::cast)
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("No OAuth2LoginAuthenticationFilter"));

        Field repositoryField =
                OAuth2LoginAuthenticationFilter.class.getDeclaredField(
                        "authorizedClientRepository");
        repositoryField.setAccessible(true);

        // This is what would drift back to Boot's default (an in-process
        // AuthenticatedPrincipalOAuth2AuthorizedClientRepository backed by
        // InMemoryOAuth2AuthorizedClientService, which never expires entries) if
        // ssoFilterChain's .authorizedClientRepository(...) override were ever removed.
        assertInstanceOf(
                HttpSessionOAuth2AuthorizedClientRepository.class,
                repositoryField.get(loginFilter));
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
        UUID userId = UUID.randomUUID();
        credentialRepository.save(Credential.ssoOnly(userId, "ana@gmail.com"));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        String code = storeCode(userId);

        exchange(code, VERIFIER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("ana"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        exchange(code, VERIFIER).andExpect(status().isBadRequest());
    }

    @Test
    void exchange_withWrongVerifier_failsAndBurnsTheCode() throws Exception {
        String code = storeCode(UUID.randomUUID());

        exchange(code, "x".repeat(43))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid or expired SSO code")));

        exchange(code, VERIFIER).andExpect(status().isBadRequest());
    }

    @Test
    void storedCode_containsNoTokensInRedis() {
        UUID userId = UUID.randomUUID();
        String code = storeCode(userId);

        String raw = redisTemplate.opsForValue().get(SsoLoginCodeStore.KEY_PREFIX + code);

        assertNotNull(raw);
        assertFalse(raw.contains("accessToken"));
        assertFalse(raw.contains("refreshToken"));
    }

    private String storeCode(UUID userId) {
        return codeStore.store(userId, CHALLENGE);
    }

    private ResultActions exchange(String code, String verifier) throws Exception {
        return mockMvc.perform(
                post("/api/1/auth/sso/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"code\":\""
                                        + code
                                        + "\",\"codeVerifier\":\""
                                        + verifier
                                        + "\"}"));
    }
}
