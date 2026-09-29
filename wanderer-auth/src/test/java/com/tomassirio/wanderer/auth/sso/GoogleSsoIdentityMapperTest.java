package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class GoogleSsoIdentityMapperTest {

    private final GoogleSsoIdentityMapper mapper = new GoogleSsoIdentityMapper();

    @Test
    void provider_isGoogleRegistrationId() {
        assertEquals("google", mapper.provider());
    }

    @Test
    void map_readsOidcClaims() {
        ExternalIdentity identity = mapper.map(oidcUser(true));

        assertEquals("google", identity.provider());
        assertEquals("1234567890", identity.subject());
        assertEquals("ana@gmail.com", identity.email());
        assertTrue(identity.emailVerified());
        assertEquals("Ana Maria", identity.name());
    }

    @Test
    void map_whenEmailNotVerified_reportsFalse() {
        assertFalse(mapper.map(oidcUser(false)).emailVerified());
    }

    @Test
    void map_whenNotOidcUser_rejects() {
        var plainUser =
                new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, Map.of("id", "1"), "id");

        assertThrows(IllegalArgumentException.class, () -> mapper.map(plainUser));
    }

    private DefaultOidcUser oidcUser(boolean emailVerified) {
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("id-token")
                        .subject("1234567890")
                        .claim("email", "ana@gmail.com")
                        .claim("email_verified", emailVerified)
                        .claim("name", "Ana Maria")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
        return new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken);
    }
}
