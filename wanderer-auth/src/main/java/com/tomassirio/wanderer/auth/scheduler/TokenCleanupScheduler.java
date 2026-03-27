package com.tomassirio.wanderer.auth.scheduler;

import com.tomassirio.wanderer.auth.service.LoginAttemptService;
import com.tomassirio.wanderer.auth.service.RevokedTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to clean up expired revoked access tokens and old login attempts. Runs hourly to
 * prevent tables from growing indefinitely.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final RevokedTokenService revokedTokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Clean up expired revoked tokens every hour. Tokens are only kept until they expire, then
     * removed to save space.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at :00
    public void cleanupExpiredTokens() {
        log.debug("Starting cleanup of expired revoked tokens");
        int deleted = revokedTokenService.cleanupExpiredTokens();
        log.debug("Cleanup complete. Removed {} expired tokens", deleted);
    }

    /**
     * Clean up old login attempts daily. Keeps last 30 days for analysis, removes older records.
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void cleanupOldLoginAttempts() {
        log.debug("Starting cleanup of old login attempts");
        int deleted = loginAttemptService.cleanupOldAttempts();
        log.debug("Cleanup complete. Removed {} old login attempts", deleted);
    }
}
