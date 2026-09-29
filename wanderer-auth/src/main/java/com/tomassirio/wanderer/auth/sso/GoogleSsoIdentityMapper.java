package com.tomassirio.wanderer.auth.sso;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/** Google is OpenID Connect: Spring has already validated the ID token before this runs. */
@Component
public class GoogleSsoIdentityMapper implements SsoIdentityMapper {

    public static final String PROVIDER = "google";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ExternalIdentity map(OAuth2User user) {
        if (!(user instanceof OidcUser oidcUser)) {
            throw new IllegalArgumentException("Google login must use OpenID Connect");
        }
        return new ExternalIdentity(
                PROVIDER,
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                oidcUser.getFullName());
    }
}
