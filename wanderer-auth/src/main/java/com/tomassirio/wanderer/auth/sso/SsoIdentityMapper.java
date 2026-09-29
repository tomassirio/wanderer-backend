package com.tomassirio.wanderer.auth.sso;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Strategy for turning a provider's authenticated user into an {@link ExternalIdentity}. One
 * implementation per SSO provider. Adding a provider = one implementation + a
 * spring.security.oauth2.client.registration.&lt;id&gt;.* block.
 */
public interface SsoIdentityMapper {

    /** Must match the Spring Security OAuth2 client registration id. */
    String provider();

    /**
     * @throws IllegalArgumentException if the user cannot be mapped
     */
    ExternalIdentity map(OAuth2User user);
}
