package com.tomassirio.wanderer.command.config;

import com.tomassirio.wanderer.command.security.JtiValidatingJwtConverter;
import com.tomassirio.wanderer.commons.security.revocation.RevokedTokenCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Configuration for JTI validation via Redis. */
@Configuration
@ConditionalOnProperty(name = "app.security.jti-validation.enabled", havingValue = "true")
public class JtiValidationConfig {

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
            RevokedTokenCache revokedTokenCache) {
        return new JtiValidatingJwtConverter(revokedTokenCache);
    }
}
