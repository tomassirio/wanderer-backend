package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

/**
 * Default resolver + PKCE towards Google, and remembers the client's return_to and its own S256
 * code_challenge (for the one-time code exchange) in the handshake session.
 */
public class ReturnToAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String RETURN_TO_PARAMETER = "return_to";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final SsoReturnUris returnUris;

    public ReturnToAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository, SsoReturnUris returnUris) {
        this.delegate =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, ApiConstants.SSO_AUTHORIZATION_BASE_URI);
        this.delegate.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
        this.returnUris = returnUris;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(
            HttpServletRequest request, String clientRegistrationId) {
        return remember(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest remember(
            HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest != null) {
            String challenge = request.getParameter(PkceParameterNames.CODE_CHALLENGE);
            String method = request.getParameter(PkceParameterNames.CODE_CHALLENGE_METHOD);
            returnUris.remember(
                    request,
                    request.getParameter(RETURN_TO_PARAMETER),
                    SsoPkce.isValidChallenge(challenge, method) ? challenge : null);
        }
        return authorizationRequest;
    }
}
