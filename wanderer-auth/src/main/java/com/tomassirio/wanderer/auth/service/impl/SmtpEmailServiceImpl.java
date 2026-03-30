package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.config.EmailProperties;
import com.tomassirio.wanderer.auth.service.EmailService;
import com.tomassirio.wanderer.commons.exception.EmailSendException;
import jakarta.annotation.PostConstruct;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * SMTP-based email service implementation using Jakarta Mail. This implementation sends actual
 * emails via an SMTP server. It is enabled when app.email.enabled=true.
 */
@Service
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailServiceImpl implements EmailService {

    private static final String VERIFICATION_EMAIL_TEMPLATE =
            "templates/email/verification-email.html";
    private static final String PASSWORD_RESET_EMAIL_TEMPLATE =
            "templates/email/password-reset-email.html";
    private static final String LOGO_RESOURCE = "assets/wanderer-logo.png";

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    @PostConstruct
    void verifySmtpConnection() {
        log.info(
                "SMTP email service active (app.email.enabled=true). Host: {}, Port: {}, From: {}",
                emailProperties.getHost(),
                emailProperties.getPort(),
                emailProperties.getFrom());

        boolean passwordSet =
                emailProperties.getPassword() != null && !emailProperties.getPassword().isBlank();
        log.info("SMTP username: {}, password set: {}", emailProperties.getUsername(), passwordSet);

        if (!passwordSet) {
            log.warn(
                    "EMAIL_PASSWORD is empty — SMTP authentication will fail. "
                            + "Set the EMAIL_PASSWORD environment variable.");
            return;
        }

        if (mailSender instanceof JavaMailSenderImpl javaMailSender) {
            try {
                javaMailSender.testConnection();
                log.info("SMTP connection test successful");
            } catch (MessagingException e) {
                if (e instanceof AuthenticationFailedException) {
                    log.error(
                            "SMTP authentication failed — check EMAIL_USERNAME and EMAIL_PASSWORD. "
                                    + "For Brevo, use your SMTP key (not your account password). "
                                    + "Error: {}",
                            e.getMessage());
                } else {
                    log.error(
                            "SMTP connection test failed — check EMAIL_HOST and EMAIL_PORT. "
                                    + "Error: {}",
                            e.getMessage());
                }
            }
        }
    }

    @Override
    @Async
    public CompletableFuture<Void> sendVerificationEmail(
            String email, String username, String verificationToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");

            helper.setFrom(emailProperties.getFrom(), emailProperties.getFromName());
            helper.setTo(email);
            helper.setSubject("Verify your email address");
            helper.setText(buildEmailContent(username, verificationToken), true);
            helper.addInline("wandererLogo", new ClassPathResource(LOGO_RESOURCE), "image/png");

            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", email);
            return CompletableFuture.completedFuture(null);
        } catch (MailAuthenticationException e) {
            log.error(
                    "SMTP authentication failed while sending email to: {}. "
                            + "Check EMAIL_USERNAME and EMAIL_PASSWORD environment variables.",
                    email,
                    e);
            throw new EmailSendException(
                    "SMTP authentication failed — verify your email credentials", e);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to: {}", email, e);
            throw new EmailSendException("Failed to send verification email", e);
        } catch (Exception e) {
            log.error("Unexpected error sending verification email to: {}", email, e);
            throw new EmailSendException("Failed to send verification email", e);
        }
    }

    private String buildEmailContent(String username, String verificationToken) {
        String baseUrl = emailProperties.getBaseUrl().replaceAll("/+$", "");
        String verificationLink = baseUrl + "/api/auth/verify-email?token=" + verificationToken;

        String template = loadTemplate(VERIFICATION_EMAIL_TEMPLATE);
        return template.replace("{{username}}", username)
                .replace("{{verificationLink}}", verificationLink)
                .replace("{{verificationToken}}", verificationToken);
    }

    @Override
    @Async
    public CompletableFuture<Void> sendPasswordResetEmail(
            String email, String username, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");

            helper.setFrom(emailProperties.getFrom(), emailProperties.getFromName());
            helper.setTo(email);
            helper.setSubject("Reset your password");
            helper.setText(buildPasswordResetEmailContent(username, resetToken), true);
            helper.addInline("wandererLogo", new ClassPathResource(LOGO_RESOURCE), "image/png");

            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", email);
            return CompletableFuture.completedFuture(null);
        } catch (MailAuthenticationException e) {
            log.error(
                    "SMTP authentication failed while sending password reset email to: {}. "
                            + "Check EMAIL_USERNAME and EMAIL_PASSWORD environment variables.",
                    email,
                    e);
            throw new EmailSendException(
                    "SMTP authentication failed — verify your email credentials", e);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new EmailSendException("Failed to send password reset email", e);
        } catch (Exception e) {
            log.error("Unexpected error sending password reset email to: {}", email, e);
            throw new EmailSendException("Failed to send password reset email", e);
        }
    }

    private String buildPasswordResetEmailContent(String username, String resetToken) {
        String baseUrl = emailProperties.getBaseUrl().replaceAll("/+$", "");
        String resetLink = baseUrl + "/api/auth/password/reset-form?token=" + resetToken;

        String template = loadTemplate(PASSWORD_RESET_EMAIL_TEMPLATE);
        return template.replace("{{username}}", username)
                .replace("{{resetLink}}", resetLink)
                .replace("{{resetToken}}", resetToken);
    }

    private String loadTemplate(String templatePath) {
        try (InputStream inputStream = new ClassPathResource(templatePath).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load email template: {}", templatePath, e);
            throw new EmailSendException("Failed to load email template: " + templatePath, e);
        }
    }
}
