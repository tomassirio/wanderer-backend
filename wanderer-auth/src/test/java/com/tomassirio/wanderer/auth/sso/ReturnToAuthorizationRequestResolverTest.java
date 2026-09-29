package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

class ReturnToAuthorizationRequestResolverTest {

    private static final String WEB = "http://localhost:3000/auth/sso-callback";
    private static final String MOBILE = "wanderer://auth/sso-callback";

    private final ReturnToAuthorizationRequestResolver resolver =
            new ReturnToAuthorizationRequestResolver(
                    new InMemoryClientRegistrationRepository(
                            CommonOAuth2Provider.GOOGLE
                                    .getBuilder("google")
                                    .clientId("id")
                                    .clientSecret("secret")
                                    .build()),
                    new SsoReturnUris(
                            new SsoProperties(List.of(WEB, MOBILE), Duration.ofSeconds(60))));

    @Test
    void resolve_authorizationRequest_addsPkceAndRemembersReturnTo() {
        MockHttpServletRequest request = request("/api/1/auth/oauth2/authorization/google");
        request.setParameter(ReturnToAuthorizationRequestResolver.RETURN_TO_PARAMETER, MOBILE);
        request.setParameter(PkceParameterNames.CODE_CHALLENGE, SsoPkceTest.CHALLENGE);
        request.setParameter(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertNotNull(authorizationRequest);
        assertTrue(
                authorizationRequest
                        .getAdditionalParameters()
                        .containsKey(PkceParameterNames.CODE_CHALLENGE));
        assertEquals(
                MOBILE, request.getSession(false).getAttribute(SsoReturnUris.SESSION_ATTRIBUTE));
        assertEquals(
                SsoPkceTest.CHALLENGE,
                request.getSession(false).getAttribute(SsoReturnUris.CODE_CHALLENGE_ATTRIBUTE));
        // Google gets Spring's own PKCE pair, never the client's challenge.
        assertNotEquals(
                SsoPkceTest.CHALLENGE,
                authorizationRequest
                        .getAdditionalParameters()
                        .get(PkceParameterNames.CODE_CHALLENGE));
    }

    @Test
    void resolve_withNonS256OrMalformedChallenge_doesNotStoreIt() {
        MockHttpServletRequest plain = request("/api/1/auth/oauth2/authorization/google");
        plain.setParameter(PkceParameterNames.CODE_CHALLENGE, SsoPkceTest.CHALLENGE);
        plain.setParameter(PkceParameterNames.CODE_CHALLENGE_METHOD, "plain");
        MockHttpServletRequest malformed = request("/api/1/auth/oauth2/authorization/google");
        malformed.setParameter(PkceParameterNames.CODE_CHALLENGE, "short");

        assertNotNull(resolver.resolve(plain));
        assertNotNull(resolver.resolve(malformed));
        assertNull(plain.getSession(false).getAttribute(SsoReturnUris.CODE_CHALLENGE_ATTRIBUTE));
        assertNull(
                malformed.getSession(false).getAttribute(SsoReturnUris.CODE_CHALLENGE_ATTRIBUTE));
    }

    @Test
    void resolve_withExplicitRegistrationId_remembersDefaultForUnknownReturnTo() {
        MockHttpServletRequest request = request("/api/1/auth/oauth2/callback/google");
        request.setParameter(
                ReturnToAuthorizationRequestResolver.RETURN_TO_PARAMETER, "https://evil.example");

        assertNotNull(resolver.resolve(request, "google"));
        assertEquals(WEB, request.getSession(false).getAttribute(SsoReturnUris.SESSION_ATTRIBUTE));
    }

    @Test
    void resolve_nonAuthorizationRequest_createsNoSession() {
        MockHttpServletRequest request = request("/api/1/auth/login");

        assertNull(resolver.resolve(request));
        assertNull(request.getSession(false));
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        return request;
    }
}
