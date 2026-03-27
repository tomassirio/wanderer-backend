package com.tomassirio.wanderer.commons.config;

import com.tomassirio.wanderer.commons.security.Role;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class JwtConverterConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.jti-validation.enabled", havingValue = "false", matchIfMissing = true)
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) roles = List.of();
            List<GrantedAuthority> authorities =
                    roles.stream()
                            .map(
                                    r ->
                                            Role.fromString(r)
                                                    .map(Role::authority)
                                                    .orElse(
                                                            r.startsWith("ROLE_")
                                                                    ? r
                                                                    : "ROLE_" + r))
                            .map(SimpleGrantedAuthority::new)
                            .map(sa -> (GrantedAuthority) sa)
                            .collect(Collectors.toList());
            String principalName = jwt.getSubject();
            return new JwtAuthenticationToken(jwt, authorities, principalName);
        };
    }
}
