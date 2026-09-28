package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Exact-match allowlist for where the SSO flow may send the user back. Never an open redirect. */
@Component
@RequiredArgsConstructor
public class SsoReturnUris {

    static final String SESSION_ATTRIBUTE = "wanderer.sso.return_to";

    private final SsoProperties ssoProperties;

    public String resolve(String requested) {
        return requested != null && ssoProperties.allowedReturnUris().contains(requested)
                ? requested
                : ssoProperties.defaultReturnUri();
    }

    public void remember(HttpServletRequest request, String requested) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, resolve(requested));
    }

    /** Reads the remembered URI and ends the handshake session (deletes it from Redis). */
    public String consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ssoProperties.defaultReturnUri();
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        session.invalidate();
        return value instanceof String uri ? resolve(uri) : ssoProperties.defaultReturnUri();
    }

    public static String withParam(String uri, String name, String value) {
        return UriComponentsBuilder.fromUriString(uri)
                .queryParam(name, value)
                .build()
                .toUriString();
    }
}
