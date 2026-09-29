package com.tomassirio.wanderer.auth.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.service.SsoService;
import com.tomassirio.wanderer.auth.sso.SsoLoginCodeStore;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SsoControllerTest {

    private static final String URL = "/api/1/auth/sso/exchange";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    @Mock private SsoLoginCodeStore ssoLoginCodeStore;
    @Mock private SsoService ssoService;
    @InjectMocks private SsoController ssoController;
    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(ssoController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void exchange_validCode_returnsLoginResponse() throws Exception {
        when(ssoLoginCodeStore.consume("good", VERIFIER)).thenReturn(Optional.of(userId));
        when(ssoService.issueTokens(userId))
                .thenReturn(new LoginResponse("access", "refresh", "Bearer", 900000L, "ana"));

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("good", VERIFIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test
    void exchange_unknownCode_returns400() throws Exception {
        when(ssoLoginCodeStore.consume("bad", VERIFIER)).thenReturn(Optional.empty());

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("bad", VERIFIER)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ssoService);
    }

    @Test
    void exchange_whenAccountDisabledAtExchange_returns400() throws Exception {
        when(ssoLoginCodeStore.consume("good", VERIFIER)).thenReturn(Optional.of(userId));
        when(ssoService.issueTokens(userId))
                .thenThrow(new IllegalArgumentException("Account disabled"));

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("good", VERIFIER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchange_blankCode_returns400() throws Exception {
        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("", VERIFIER)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ssoService);
    }

    @Test
    void exchange_missingVerifier_returns400() throws Exception {
        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"good\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ssoLoginCodeStore);
        verifyNoInteractions(ssoService);
    }

    @Test
    void exchange_malformedVerifier_returns400() throws Exception {
        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("good", "too+short")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ssoLoginCodeStore);
        verifyNoInteractions(ssoService);
    }

    private static String body(String code, String verifier) {
        return "{\"code\":\"" + code + "\",\"codeVerifier\":\"" + verifier + "\"}";
    }
}
