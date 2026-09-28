package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class SsoReturnUrisTest {

    private static final String WEB = "http://localhost:3000/auth/sso-callback";
    private static final String MOBILE = "wanderer://auth/sso-callback";

    private final SsoReturnUris returnUris =
            new SsoReturnUris(new SsoProperties(List.of(WEB, MOBILE), Duration.ofSeconds(60)));

    @Test
    void resolve_keepsAllowedUri() {
        assertEquals(MOBILE, returnUris.resolve(MOBILE));
    }

    @Test
    void resolve_fallsBackToDefaultForUnknownOrMissing() {
        assertEquals(WEB, returnUris.resolve("https://evil.example/steal"));
        assertEquals(WEB, returnUris.resolve(MOBILE + "/extra"));
        assertEquals(WEB, returnUris.resolve(null));
    }

    @Test
    void rememberThenConsume_roundTripsAndInvalidatesSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        returnUris.remember(request, MOBILE, SsoPkceTest.CHALLENGE);
        MockHttpSession session = (MockHttpSession) request.getSession(false);

        assertEquals(
                new SsoReturnUris.Handshake(MOBILE, SsoPkceTest.CHALLENGE),
                returnUris.consume(request));
        assertTrue(session.isInvalid());
    }

    @Test
    void consume_withoutChallenge_returnsNullChallenge() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        returnUris.remember(request, MOBILE, null);

        assertEquals(new SsoReturnUris.Handshake(MOBILE, null), returnUris.consume(request));
    }

    @Test
    void consume_withoutSession_returnsDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(new SsoReturnUris.Handshake(WEB, null), returnUris.consume(request));
        assertNull(request.getSession(false));
    }

    @Test
    void consume_whenSessionStoreFails_returnsDefault() {
        MockHttpSession session =
                new MockHttpSession() {
                    @Override
                    public Object getAttribute(String name) {
                        throw new IllegalStateException("Redis unavailable");
                    }
                };
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        assertEquals(new SsoReturnUris.Handshake(WEB, null), returnUris.consume(request));
    }

    @Test
    void withParam_appendsQueryParameter() {
        assertEquals(MOBILE + "?code=abc", SsoReturnUris.withParam(MOBILE, "code", "abc"));
    }
}
