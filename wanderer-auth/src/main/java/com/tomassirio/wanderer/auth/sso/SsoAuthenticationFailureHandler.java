package com.tomassirio.wanderer.auth.sso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** User cancelled, state mismatch, expired session, invalid ID token, etc. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SsoAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final SsoReturnUris returnUris;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        log.warn("SSO authentication failed: {}", exception.getMessage());
        response.sendRedirect(
                SsoReturnUris.withParam(returnUris.consume(request), "error", "sso_failed"));
    }
}
