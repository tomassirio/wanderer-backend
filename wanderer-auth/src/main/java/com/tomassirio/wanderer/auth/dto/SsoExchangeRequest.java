package com.tomassirio.wanderer.auth.dto;

import com.tomassirio.wanderer.auth.sso.SsoPkce;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * @param code one-time code from the SSO redirect
 * @param codeVerifier RFC 7636 verifier whose S256 challenge started the login
 */
public record SsoExchangeRequest(
        @NotBlank(message = "Code is required") String code,
        @NotBlank(message = "Code verifier is required")
                @Pattern(regexp = SsoPkce.VERIFIER_REGEX, message = "Invalid code verifier")
                String codeVerifier) {}
