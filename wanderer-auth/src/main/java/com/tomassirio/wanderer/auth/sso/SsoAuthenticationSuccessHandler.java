package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.auth.service.SsoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Maps the provider user via its strategy, signs in, and redirects back with a one-time code. */
@Component
@Slf4j
public class SsoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final Map<String, SsoIdentityMapper> mappers;
    private final SsoService ssoService;
    private final SsoLoginCodeStore codeStore;
    private final SsoReturnUris returnUris;

    public SsoAuthenticationSuccessHandler(
            List<SsoIdentityMapper> mappers,
            SsoService ssoService,
            SsoLoginCodeStore codeStore,
            SsoReturnUris returnUris) {
        this.mappers =
                mappers.stream()
                        .collect(
                                Collectors.toMap(SsoIdentityMapper::provider, Function.identity()));
        this.ssoService = ssoService;
        this.codeStore = codeStore;
        this.returnUris = returnUris;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        String returnTo = returnUris.consume(request);
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        try {
            SsoIdentityMapper mapper = mappers.get(provider);
            if (mapper == null) {
                throw new IllegalArgumentException("Unsupported SSO provider: " + provider);
            }
            String code = codeStore.store(ssoService.signIn(mapper.map(token.getPrincipal())));
            log.info("SSO login succeeded via {}", provider);
            response.sendRedirect(SsoReturnUris.withParam(returnTo, "code", code));
        } catch (RuntimeException e) {
            log.warn("SSO login via {} failed: {}", provider, e.getMessage());
            response.sendRedirect(SsoReturnUris.withParam(returnTo, "error", "sso_failed"));
        }
    }
}
