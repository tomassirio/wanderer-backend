package com.tomassirio.wanderer.query.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Feign client for checking token revocation status in the auth service. */
@FeignClient(name = "wanderer-auth-token-validator-query", url = "${app.auth-service.url}")
public interface AuthTokenValidatorClient {

    /**
     * Check if a JTI is revoked.
     *
     * @param jti the JWT ID
     * @return true if the token is revoked
     */
    @GetMapping("/api/1/auth/internal/token/revoked")
    boolean isTokenRevoked(@RequestParam("jti") String jti);
}
