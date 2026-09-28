package com.tomassirio.wanderer.auth.controller;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.SsoExchangeRequest;
import com.tomassirio.wanderer.auth.sso.SsoLoginCodeStore;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO completion endpoint. Starting a login is a browser navigation to {@code
 * /api/1/auth/oauth2/authorization/{provider}?return_to=...}, handled by Spring Security.
 */
@RestController
@RequestMapping(value = ApiConstants.AUTH_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SSO", description = "Single sign-on login completion")
public class SsoController {

    private final SsoLoginCodeStore ssoLoginCodeStore;

    @PostMapping(
            value = ApiConstants.SSO_EXCHANGE_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Exchange SSO login code",
            description =
                    "Exchanges the one-time code from the SSO redirect for access and refresh"
                            + " tokens. Each code works once and expires quickly.")
    public ResponseEntity<LoginResponse> exchange(@Valid @RequestBody SsoExchangeRequest request) {
        LoginResponse response =
                ssoLoginCodeStore
                        .consume(request.code())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Invalid or expired SSO code"));
        log.info("SSO code exchanged");
        return ResponseEntity.ok(response);
    }
}
