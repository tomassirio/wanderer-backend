package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.domain.LoginAttempt;
import com.tomassirio.wanderer.auth.repository.LoginAttemptRepository;
import com.tomassirio.wanderer.auth.service.LoginAttemptService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for tracking login attempts and providing brute-force protection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;

    @Value("${app.security.max-failed-attempts:10}")
    private int maxFailedAttempts;

    @Value("${app.security.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    @Value("${app.security.attempt-window-minutes:30}")
    private int attemptWindowMinutes;

    @Override
    @Transactional
    public void recordSuccessfulLogin(String identifier, UUID userId, String ipAddress) {
        LoginAttempt attempt = LoginAttempt.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .identifier(identifier.toLowerCase())
                .ipAddress(ipAddress)
                .success(true)
                .build();

        loginAttemptRepository.save(attempt);
        log.debug("Recorded successful login for identifier: {}", identifier);
    }

    @Override
    @Transactional
    public void recordFailedLogin(String identifier, String ipAddress) {
        LoginAttempt attempt = LoginAttempt.builder()
                .id(UUID.randomUUID())
                .identifier(identifier.toLowerCase())
                .ipAddress(ipAddress)
                .success(false)
                .build();

        loginAttemptRepository.save(attempt);
        log.warn("Recorded failed login attempt for identifier: {} from IP: {}", identifier, ipAddress);
    }

    @Override
    public boolean isAccountLocked(String identifier) {
        Instant windowStart = Instant.now().minus(attemptWindowMinutes, ChronoUnit.MINUTES);
        int failedAttempts = loginAttemptRepository.countRecentFailedAttempts(
                identifier.toLowerCase(), 
                windowStart
        );

        boolean locked = failedAttempts >= maxFailedAttempts;
        
        if (locked) {
            log.warn("Account locked for identifier: {} ({} failed attempts)", identifier, failedAttempts);
        }
        
        return locked;
    }

    @Override
    @Transactional
    public int cleanupOldAttempts() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = loginAttemptRepository.deleteOldAttempts(cutoff);
        
        if (deleted > 0) {
            log.info("Cleaned up {} old login attempts", deleted);
        }
        
        return deleted;
    }
}
