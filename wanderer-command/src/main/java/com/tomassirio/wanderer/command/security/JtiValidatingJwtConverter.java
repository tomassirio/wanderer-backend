package com.tomassirio.wanderer.command.security;

import com.tomassirio.wanderer.commons.security.revocation.RevokedTokenCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom JWT authentication converter that checks if the token's JTI is revoked via Redis.
 */
@RequiredArgsConstructor
@Slf4j
public class JtiValidatingJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final RevokedTokenCache revokedTokenCache;

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        // Check if JTI is revoked via Redis
        String jti = jwt.getId();
        if (jti != null && revokedTokenCache.isTokenRevoked(jti)) {
            log.warn("Attempted to use revoked token with JTI: {}", jti);
            throw new IllegalArgumentException("Token has been revoked");
        }

        // Extract roles from JWT claims
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<?> roles = jwt.getClaim("roles");
        if (roles == null) {
            return List.of();
        }

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
