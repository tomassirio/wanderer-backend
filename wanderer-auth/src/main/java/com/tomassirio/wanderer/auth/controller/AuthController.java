package com.tomassirio.wanderer.auth.controller;

import com.tomassirio.wanderer.auth.dto.LoginRequest;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.PasswordChangeRequest;
import com.tomassirio.wanderer.auth.dto.PasswordResetConfirmRequest;
import com.tomassirio.wanderer.auth.dto.PasswordResetRequest;
import com.tomassirio.wanderer.auth.dto.RefreshTokenRequest;
import com.tomassirio.wanderer.auth.dto.RefreshTokenResponse;
import com.tomassirio.wanderer.auth.dto.RegisterPendingResponse;
import com.tomassirio.wanderer.auth.dto.RegisterRequest;
import com.tomassirio.wanderer.auth.dto.VerifyEmailRequest;
import com.tomassirio.wanderer.auth.service.AuthService;
import com.tomassirio.wanderer.auth.service.TokenService;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations. Handles user login, registration, logout, token
 * refresh, and password management.
 *
 * @since 0.1.8
 */
@RestController
@RequestMapping(value = ApiConstants.AUTH_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for user authentication and registration")
public class AuthController {

    private static final String VERIFICATION_SUCCESS_TEMPLATE =
            "templates/email/verification-success.html";
    private static final String VERIFICATION_FAILURE_TEMPLATE =
            "templates/email/verification-failure.html";
    private static final String PASSWORD_RESET_FORM_TEMPLATE =
            "templates/email/password-reset-form.html";
    private static final String LOGO_RESOURCE = "assets/wanderer-logo.png";

    private final AuthService authService;
    private final TokenService tokenService;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String baseUrl;

    @PostMapping(value = ApiConstants.LOGIN_ENDPOINT, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "User login",
            description =
                    "Authenticates a user with username or email and returns access and refresh tokens")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Login attempt received");
        String ipAddress = extractIpAddress(httpRequest);
        LoginResponse response =
                authService.login(request.identifier(), request.password(), ipAddress);
        log.info("Login successful");
        return ResponseEntity.ok(response);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping(
            value = ApiConstants.REGISTER_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "User registration",
            description =
                    "Initiates user registration by sending an email verification link. The user"
                            + " account is created only after email verification.")
    public ResponseEntity<RegisterPendingResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt received");
        RegisterPendingResponse response = authService.register(request);
        log.info("Registration initiated, verification email sent");
        return ResponseEntity.status(202).body(response);
    }

    @PostMapping(
            value = ApiConstants.VERIFY_EMAIL_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Verify email",
            description =
                    "Verifies the user's email address using the token sent via email. Upon"
                            + " successful verification, creates the user account and returns access"
                            + " and refresh tokens.")
    public ResponseEntity<LoginResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        log.info("Email verification attempt via POST");
        LoginResponse response = authService.verifyEmail(request.token());
        log.info("Email verified successfully via POST");
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping(value = ApiConstants.VERIFY_EMAIL_ENDPOINT, produces = MediaType.TEXT_HTML_VALUE)
    @Operation(
            summary = "Verify email via link",
            description =
                    "Verifies the user's email address via a clickable link from the verification"
                            + " email. Returns an HTML page with the result.")
    public ResponseEntity<String> verifyEmailViaLink(@RequestParam("token") String token) {
        try {
            LoginResponse response = authService.verifyEmail(token);
            log.info("Email verified successfully via link");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(
                            loadAndPopulateTemplate(
                                    VERIFICATION_SUCCESS_TEMPLATE, response.username()));
        } catch (Exception e) {
            log.warn("Email verification via link failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body(loadAndPopulateTemplate(VERIFICATION_FAILURE_TEMPLATE, null));
        }
    }

    private String loadAndPopulateTemplate(String templatePath, String username) {
        String template = loadTemplate(templatePath);
        String loginUrl = baseUrl.replaceAll("/+$", "") + "/login";
        if (username != null && !username.isBlank()) {
            loginUrl += "?username=" + username;
        }
        String homeUrl = baseUrl.replaceAll("/+$", "");
        String logoDataUri = buildLogoDataUri();
        return template.replace("{{loginUrl}}", loginUrl)
                .replace("{{homeUrl}}", homeUrl)
                .replace("{{logoSrc}}", logoDataUri);
    }

    private String buildLogoDataUri() {
        try (InputStream inputStream = new ClassPathResource(LOGO_RESOURCE).getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("Failed to load logo for verification page", e);
            return "";
        }
    }

    private String loadTemplate(String templatePath) {
        try (InputStream inputStream = new ClassPathResource(templatePath).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load template: {}", templatePath, e);
            return "<html><body><h1>An error occurred</h1></body></html>";
        }
    }

    @PostMapping(ApiConstants.LOGOUT_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "User logout",
            description =
                    "Invalidates the current access token and all refresh tokens for the user",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String jti = jwt.getId(); // Get JTI from JWT
        log.info("Logout request for userId: {}", userId);
        authService.logout(userId, jti, jwt.getExpiresAt());
        log.info("Logout successful for userId: {}", userId);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping(value = ApiConstants.REFRESH_ENDPOINT, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Refresh access token",
            description = "Exchanges a refresh token for a new access token and refresh token")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        RefreshTokenResponse response = tokenService.refreshAccessToken(request.refreshToken());
        log.info("Token refresh successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = ApiConstants.PASSWORD_RESET_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Initiate password reset",
            description = "Sends a password reset email with a link to the password reset form")
    public ResponseEntity<Map<String, String>> initiatePasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        log.info("Password reset initiated");
        authService.initiatePasswordReset(request.email());
        log.info("Password reset email sent");
        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "If an account with that email exists, a password reset email has been sent"));
    }

    @GetMapping(
            value = ApiConstants.PASSWORD_RESET_FORM_ENDPOINT,
            produces = MediaType.TEXT_HTML_VALUE)
    @Operation(
            summary = "Password reset form",
            description =
                    "Displays the password reset form where users can enter their new password."
                            + " This page is linked from the password reset email.")
    public ResponseEntity<String> passwordResetForm(@RequestParam("token") String token) {
        log.info("Password reset form requested");
        String template = loadTemplate(PASSWORD_RESET_FORM_TEMPLATE);
        String loginUrl = baseUrl.replaceAll("/+$", "") + "/login";
        String logoDataUri = buildLogoDataUri();
        String resetApiUrl =
                baseUrl.replaceAll("/+$", "") + "/api/auth" + ApiConstants.PASSWORD_RESET_ENDPOINT;
        String html =
                template.replace("{{token}}", token)
                        .replace("{{loginUrl}}", loginUrl)
                        .replace("{{logoSrc}}", logoDataUri)
                        .replace("{{resetApiUrl}}", resetApiUrl);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PutMapping(
            value = ApiConstants.PASSWORD_RESET_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Complete password reset",
            description = "Resets the password using a valid reset token")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        log.info("Password reset confirmation attempt");
        String username = authService.resetPassword(request.token(), request.newPassword());
        log.info("Password reset completed successfully");
        Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Password reset successfully");
        if (username != null) {
            response.put("username", username);
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping(
            value = ApiConstants.PASSWORD_CHANGE_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Change password",
            description = "Changes the password for the authenticated user",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody PasswordChangeRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("Password change request for userId: {}", userId);
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        log.info("Password changed successfully for userId: {}", userId);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
