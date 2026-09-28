package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Owns the SSO handshake session: the allowlisted return URI (exact match, never an open redirect)
 * and the client's PKCE code challenge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SsoReturnUris {

    static final String SESSION_ATTRIBUTE = "wanderer.sso.return_to";
    static final String CODE_CHALLENGE_ATTRIBUTE = "wanderer.sso.code_challenge";

    private final SsoProperties ssoProperties;

    /**
     * @param codeChallenge validated client challenge, or null when the client sent none/invalid
     */
    public record Handshake(String returnTo, String codeChallenge) {}

    public String resolve(String requested) {
        return requested != null && ssoProperties.allowedReturnUris().contains(requested)
                ? requested
                : ssoProperties.defaultReturnUri();
    }

    public void remember(HttpServletRequest request, String requested, String codeChallenge) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_ATTRIBUTE, resolve(requested));
        if (codeChallenge != null) {
            session.setAttribute(CODE_CHALLENGE_ATTRIBUTE, codeChallenge);
        }
    }

    /**
     * Reads the handshake and ends the session (deletes it from Redis). If the session store fails,
     * falls back to the default URI so the user still gets an error redirect instead of a 500.
     */
    public Handshake consume(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return new Handshake(ssoProperties.defaultReturnUri(), null);
            }
            Object returnTo = session.getAttribute(SESSION_ATTRIBUTE);
            Object challenge = session.getAttribute(CODE_CHALLENGE_ATTRIBUTE);
            session.invalidate();
            return new Handshake(
                    returnTo instanceof String uri
                            ? resolve(uri)
                            : ssoProperties.defaultReturnUri(),
                    challenge instanceof String c && SsoPkce.isValidChallenge(c, null) ? c : null);
        } catch (RuntimeException e) {
            log.warn("Could not read SSO handshake session, using default return URI", e);
            return new Handshake(ssoProperties.defaultReturnUri(), null);
        }
    }

    public static String withParam(String uri, String name, String value) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParam(name, value)
                .build()
                .toUriString();
    }
}
