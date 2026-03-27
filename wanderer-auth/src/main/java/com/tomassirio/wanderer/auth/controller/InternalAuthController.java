package com.tomassirio.wanderer.auth.controller;

import com.tomassirio.wanderer.auth.service.RevokedTokenService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API endpoints for inter-service communication. Not exposed in public API documentation.
 */
@RestController
@RequestMapping("/api/1/auth/internal")
@RequiredArgsConstructor
@Hidden
public class InternalAuthController {

    private final RevokedTokenService revokedTokenService;

    /**
     * Check if a token is revoked. Called by other services to validate JTI against the blacklist.
     *
     * @param jti the JWT ID
     * @return true if the token is revoked
     */
    @GetMapping("/token/revoked")
    public boolean isTokenRevoked(@RequestParam("jti") String jti) {
        return revokedTokenService.isTokenRevoked(jti);
    }
}
