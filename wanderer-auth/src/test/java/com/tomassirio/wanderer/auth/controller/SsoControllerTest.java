package com.tomassirio.wanderer.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.sso.SsoLoginCodeStore;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import java.util.Optional;
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

    @Mock private SsoLoginCodeStore ssoLoginCodeStore;
    @InjectMocks private SsoController ssoController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(ssoController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void exchange_validCode_returnsLoginResponse() throws Exception {
        when(ssoLoginCodeStore.consume("good"))
                .thenReturn(
                        Optional.of(
                                new LoginResponse("access", "refresh", "Bearer", 900000L, "ana")));

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test
    void exchange_unknownCode_returns400() throws Exception {
        when(ssoLoginCodeStore.consume("bad")).thenReturn(Optional.empty());

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchange_blankCode_returns400() throws Exception {
        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
