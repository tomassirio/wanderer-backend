package com.tomassirio.wanderer.auth.config;

import com.tomassirio.wanderer.auth.security.JtiValidatingJwtConverter;
import com.tomassirio.wanderer.auth.sso.ReturnToAuthorizationRequestResolver;
import com.tomassirio.wanderer.auth.sso.SsoAuthenticationFailureHandler;
import com.tomassirio.wanderer.auth.sso.SsoAuthenticationSuccessHandler;
import com.tomassirio.wanderer.auth.sso.SsoReturnUris;
import com.tomassirio.wanderer.commons.config.JwtConfig;
import com.tomassirio.wanderer.commons.config.RateLimitConfig;
import com.tomassirio.wanderer.commons.config.SecurityCorsConfig;
import com.tomassirio.wanderer.commons.config.SecurityHeadersConfig;
import com.tomassirio.wanderer.commons.config.SecurityHeadersConfig.SecurityHeadersCustomizer;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Import({
    JwtConfig.class,
    SecurityCorsConfig.class,
    SecurityHeadersConfig.class,
    RateLimitConfig.class
})
public class SecurityConfig {

    private final JtiValidatingJwtConverter jtiValidatingJwtConverter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final SecurityHeadersCustomizer securityHeadersCustomizer;

    /**
     * SSO redirect handshake. The only chain that may create an HTTP session (stored in Redis by
     * Spring Session) — it holds OAuth state/nonce/PKCE and return_to for a few minutes.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain ssoFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            SsoReturnUris ssoReturnUris,
            SsoAuthenticationSuccessHandler successHandler,
            SsoAuthenticationFailureHandler failureHandler)
            throws Exception {
        http.securityMatcher(ApiConstants.AUTH_PATH + "/oauth2/**")
                // Callback is protected by the OAuth state parameter, not CSRF tokens
                .csrf(AbstractHttpConfigurer::disable)
                .headers(securityHeadersCustomizer::configure)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // Never keep the Spring SecurityContext in the session: we hand out JWTs instead
                .securityContext(
                        context ->
                                context.securityContextRepository(
                                        new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .oauth2Login(
                        oauth2 ->
                                oauth2.authorizedClientRepository(
                                                new HttpSessionOAuth2AuthorizedClientRepository())
                                        .authorizationEndpoint(
                                                endpoint ->
                                                        endpoint.baseUri(
                                                                        ApiConstants
                                                                                .SSO_AUTHORIZATION_BASE_URI)
                                                                .authorizationRequestResolver(
                                                                        new ReturnToAuthorizationRequestResolver(
                                                                                clientRegistrationRepository,
                                                                                ssoReturnUris)))
                                        .redirectionEndpoint(
                                                endpoint ->
                                                        endpoint.baseUri(
                                                                ApiConstants.SSO_CALLBACK_BASE_URI
                                                                        + "/*"))
                                        .successHandler(successHandler)
                                        .failureHandler(failureHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(securityHeadersCustomizer::configure)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authz ->
                                authz.requestMatchers("/api/1/auth/**")
                                        .permitAll()
                                        .requestMatchers("/assets/**")
                                        .permitAll()
                                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                                        .permitAll()
                                        .requestMatchers("/actuator/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jtiValidatingJwtConverter)));
        return http.build();
    }
}
