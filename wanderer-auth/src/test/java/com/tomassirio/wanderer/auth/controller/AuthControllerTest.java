package com.tomassirio.wanderer.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.auth.dto.LoginRequest;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.PasswordChangeRequest;
import com.tomassirio.wanderer.auth.dto.PasswordResetConfirmRequest;
import com.tomassirio.wanderer.auth.dto.RegisterPendingResponse;
import com.tomassirio.wanderer.auth.dto.RegisterRequest;
import com.tomassirio.wanderer.auth.service.AuthService;
import com.tomassirio.wanderer.auth.service.TokenService;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private AuthService authService;

    @Mock private TokenService tokenService;

    @InjectMocks private AuthController authController;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authController, "baseUrl", "http://localhost:3000");
        mockMvc =
                MockMvcBuilders.standaloneSetup(authController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(
                                new org.springframework.security.web.method.annotation
                                        .AuthenticationPrincipalArgumentResolver())
                        .build();
        SecurityContextHolder.clearContext();
    }

    /** Helper method to create a JWT authentication request post processor */
    private RequestPostProcessor jwtAuth(String subject) {
        return request -> {
            Jwt jwt =
                    Jwt.withTokenValue("mock.jwt.token")
                            .header("alg", "HS256")
                            .subject(subject)
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(3600))
                            .build();
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return request;
        };
    }

    @Test
    void login_whenValidRequest_shouldReturnOk() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "password123");
        LoginResponse response =
                new LoginResponse(
                        "jwt.access.token", "refresh.token", "Bearer", 3600000L, "testuser");

        when(authService.login(request.identifier(), request.password())).thenReturn(response);

        mockMvc.perform(
                        post("/api/1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600000L));
    }

    @Test
    void login_whenInvalidPassword_shouldReturnBadRequest() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "short");

        mockMvc.perform(
                        post("/api/1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_whenValidRequest_shouldReturnAccepted() throws Exception {
        RegisterRequest request =
                new RegisterRequest("testuser", "test@example.com", "password123");
        RegisterPendingResponse response =
                new RegisterPendingResponse(
                        "Registration pending. Please check your email to verify your account.");

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post("/api/1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Registration pending. Please check your email to verify"
                                                + " your account."));
    }

    @Test
    void register_whenInvalidEmail_shouldReturnBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "invalid-email", "password123");

        mockMvc.perform(
                        post("/api/1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_whenValidRequest_shouldReturnOk() throws Exception {
        com.tomassirio.wanderer.auth.dto.RefreshTokenRequest request =
                new com.tomassirio.wanderer.auth.dto.RefreshTokenRequest("valid.refresh.token");
        com.tomassirio.wanderer.auth.dto.RefreshTokenResponse response =
                new com.tomassirio.wanderer.auth.dto.RefreshTokenResponse(
                        "new.access.token", "new.refresh.token", "Bearer", 3600000L);

        when(tokenService.refreshAccessToken(request.refreshToken())).thenReturn(response);

        mockMvc.perform(
                        post("/api/1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new.refresh.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600000L));
    }

    @Test
    void passwordReset_whenValidEmail_shouldReturnOk() throws Exception {
        com.tomassirio.wanderer.auth.dto.PasswordResetRequest request =
                new com.tomassirio.wanderer.auth.dto.PasswordResetRequest("test@example.com");

        when(authService.initiatePasswordReset(request.email())).thenReturn("reset.token.here");

        mockMvc.perform(
                        post("/api/1/auth/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "If an account with that email exists, a password reset"
                                                + " email has been sent"));
    }

    @Test
    void passwordResetForm_whenTokenProvided_shouldReturnHtml() throws Exception {
        mockMvc.perform(
                        get("/api/1/auth/password/reset-form")
                                .param("token", "some.reset.token")
                                .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "Reset your password")))
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("some.reset.token")));
    }

    @Test
    void logout_whenValidToken_shouldReturnOk() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(authService).logout(userId);

        mockMvc.perform(post("/api/1/auth/logout").with(jwtAuth(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService).logout(userId);
    }

    @Test
    void resetPassword_whenValidToken_shouldReturnOk() throws Exception {
        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest("valid.reset.token", "newPassword123");

        when(authService.resetPassword(request.token(), request.newPassword()))
                .thenReturn("testuser");

        mockMvc.perform(
                        put("/api/1/auth/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"))
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(authService).resetPassword(request.token(), request.newPassword());
    }

    @Test
    void resetPassword_whenInvalidPassword_shouldReturnBadRequest() throws Exception {
        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest("valid.reset.token", "short");

        mockMvc.perform(
                        put("/api/1/auth/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_whenValidRequest_shouldReturnOk() throws Exception {
        UUID userId = UUID.randomUUID();
        PasswordChangeRequest request =
                new PasswordChangeRequest("currentPassword123", "newPassword456");

        doNothing()
                .when(authService)
                .changePassword(userId, request.currentPassword(), request.newPassword());

        mockMvc.perform(
                        put("/api/1/auth/password/change")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(jwtAuth(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        verify(authService)
                .changePassword(userId, request.currentPassword(), request.newPassword());
    }

    @Test
    void changePassword_whenInvalidNewPassword_shouldReturnBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        PasswordChangeRequest request = new PasswordChangeRequest("currentPassword123", "short");

        mockMvc.perform(
                        put("/api/1/auth/password/change")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(jwtAuth(userId.toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmailViaLink_whenValidToken_shouldReturnHtmlSuccess() throws Exception {
        LoginResponse response =
                new LoginResponse(
                        "jwt.access.token", "refresh.token", "Bearer", 3600000L, "testuser");
        when(authService.verifyEmail("valid.token")).thenReturn(response);

        mockMvc.perform(
                        get("/api/1/auth/verify-email")
                                .param("token", "valid.token")
                                .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("Email Verified!")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "Your email has been verified successfully")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "/login?username=testuser")));
    }

    @Test
    void verifyEmailViaLink_whenInvalidToken_shouldReturnHtmlError() throws Exception {
        when(authService.verifyEmail("invalid.token"))
                .thenThrow(new IllegalArgumentException("Invalid token"));

        mockMvc.perform(
                        get("/api/1/auth/verify-email")
                                .param("token", "invalid.token")
                                .accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "Verification Failed")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "invalid or has expired")));
    }
}
