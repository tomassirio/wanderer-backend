package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.service.SsoService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

@ExtendWith(MockitoExtension.class)
class SsoAuthenticationHandlersTest {

    private static final String WEB = "http://localhost:3000/auth/sso-callback";
    private static final String MOBILE = "wanderer://auth/sso-callback";

    @Mock private SsoService ssoService;
    @Mock private SsoLoginCodeStore codeStore;

    private SsoReturnUris returnUris;
    private SsoAuthenticationSuccessHandler successHandler;
    private SsoAuthenticationFailureHandler failureHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        returnUris =
                new SsoReturnUris(new SsoProperties(List.of(WEB, MOBILE), Duration.ofSeconds(60)));
        successHandler =
                new SsoAuthenticationSuccessHandler(
                        List.of(new GoogleSsoIdentityMapper()), ssoService, codeStore, returnUris);
        failureHandler = new SsoAuthenticationFailureHandler(returnUris);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        returnUris.remember(request, MOBILE);
    }

    @Test
    void success_redirectsToReturnUriWithCode() throws Exception {
        LoginResponse login = new LoginResponse("a", "r", "Bearer", 1L, "ana");
        when(ssoService.signIn(any())).thenReturn(login);
        when(codeStore.store(login)).thenReturn("abc");

        successHandler.onAuthenticationSuccess(request, response, token("google"));

        assertEquals(MOBILE + "?code=abc", response.getRedirectedUrl());
    }

    @Test
    void success_whenSignInFails_redirectsWithError() throws Exception {
        when(ssoService.signIn(any())).thenThrow(new IllegalArgumentException("Account disabled"));

        successHandler.onAuthenticationSuccess(request, response, token("google"));

        assertEquals(MOBILE + "?error=sso_failed", response.getRedirectedUrl());
        verify(codeStore, never()).store(any());
    }

    @Test
    void success_whenProviderHasNoMapper_redirectsWithError() throws Exception {
        successHandler.onAuthenticationSuccess(request, response, token("github"));

        assertEquals(MOBILE + "?error=sso_failed", response.getRedirectedUrl());
        verify(ssoService, never()).signIn(any());
    }

    @Test
    void failure_redirectsWithError() throws Exception {
        failureHandler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertEquals(MOBILE + "?error=sso_failed", response.getRedirectedUrl());
    }

    private OAuth2AuthenticationToken token(String registrationId) {
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("t")
                        .subject("sub-1")
                        .claim("email", "ana@gmail.com")
                        .claim("email_verified", true)
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        return new OAuth2AuthenticationToken(
                new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken),
                AuthorityUtils.NO_AUTHORITIES,
                registrationId);
    }
}
