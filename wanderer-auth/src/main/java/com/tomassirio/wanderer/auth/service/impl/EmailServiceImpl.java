package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.service.EmailService;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email service implementation that logs verification emails to the console. This is useful for
 * development and testing. This implementation is active when app.email.enabled is false or not
 * set.
 */
@Service
@ConditionalOnProperty(
        prefix = "app.email",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    @PostConstruct
    void logMode() {
        log.info(
                "Console email service active (app.email.enabled=false). "
                        + "Set EMAIL_ENABLED=true to send real emails via SMTP.");
    }

    @Override
    @Async
    public CompletableFuture<Void> sendVerificationEmail(String email, String username, String verificationToken) {
        // For now, just log the email content
        // In production, this would send an actual email via SMTP
        log.info("========================================");
        log.info("EMAIL VERIFICATION");
        log.info("========================================");
        log.info("To: {}", email);
        log.info("Subject: Verify your email address");
        log.info("----------------------------------------");
        log.info("Hello {},", username);
        log.info("");
        log.info("Thank you for registering!");
        log.info("");
        log.info("Please verify your email address by using the following token:");
        log.info("");
        log.info("Token: {}", verificationToken);
        log.info("");
        log.info(
                "You can verify your email by sending a POST request to /api/1/auth/verify-email"
                        + " with this token, or visit /verify-email?token=<token> on the frontend.");
        log.info("");
        log.info("This token will expire in 24 hours.");
        log.info("");
        log.info("If you did not create an account, please ignore this email.");
        log.info("========================================");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendPasswordResetEmail(String email, String username, String resetToken) {
        log.info("========================================");
        log.info("PASSWORD RESET");
        log.info("========================================");
        log.info("To: {}", email);
        log.info("Subject: Reset your password");
        log.info("----------------------------------------");
        log.info("Hello {},", username);
        log.info("");
        log.info("We received a request to reset your password.");
        log.info("");
        log.info("Please use the following token to reset your password:");
        log.info("");
        log.info("Token: {}", resetToken);
        log.info("");
        log.info(
                "You can reset your password by sending a PUT request to /api/1/auth/password/reset"
                        + " with this token and your new password, or visit"
                        + " /api/auth/password/reset-form?token=<token> in your browser.");
        log.info("");
        log.info("This token will expire in 1 hour.");
        log.info("");
        log.info("If you did not request a password reset, please ignore this email.");
        log.info("========================================");
        return CompletableFuture.completedFuture(null);
    }
}
