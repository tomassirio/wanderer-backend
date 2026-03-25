package com.tomassirio.wanderer.command.config;

import com.tomassirio.wanderer.commons.config.JwtConfig;
import com.tomassirio.wanderer.commons.config.JwtConverterConfig;
import com.tomassirio.wanderer.commons.config.RateLimitConfig;
import com.tomassirio.wanderer.commons.config.SecurityCorsConfig;
import com.tomassirio.wanderer.commons.config.SecurityHeadersConfig;
import com.tomassirio.wanderer.commons.config.SecurityHeadersConfig.SecurityHeadersCustomizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Import({
    JwtConfig.class,
    JwtConverterConfig.class,
    SecurityCorsConfig.class,
    SecurityHeadersConfig.class,
    RateLimitConfig.class
})
public class SecurityConfig {

    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final SecurityHeadersCustomizer securityHeadersCustomizer;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(securityHeadersCustomizer::configure)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authz ->
                                authz.requestMatchers("/api/1/users")
                                        .permitAll()
                                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                                        .permitAll()
                                        .requestMatchers("/actuator/**")
                                        .permitAll()
                                        .requestMatchers("/ws", "/ws/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter)));
        return http.build();
    }
}
