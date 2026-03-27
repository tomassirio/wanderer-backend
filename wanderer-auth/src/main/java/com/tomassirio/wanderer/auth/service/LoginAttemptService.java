package com.tomassirio.wanderer.auth.service;

import java.util.UUID;

/** Service for tracking and managing login attempts for brute-force protection. */
public interface LoginAttemptService {

    /**
     * Record a successful login attempt.
     *
     * @param identifier username or email used to login
     * @param userId the user ID
     * @param ipAddress the IP address of the client
     */
    void recordSuccessfulLogin(String identifier, UUID userId, String ipAddress);

    /**
     * Record a failed login attempt.
     *
     * @param identifier username or email attempted
     * @param ipAddress the IP address of the client
     */
    void recordFailedLogin(String identifier, String ipAddress);

    /**
     * Check if an identifier is locked due to too many failed attempts.
     *
     * @param identifier username or email to check
     * @return true if the account is temporarily locked
     */
    boolean isAccountLocked(String identifier);

    /**
     * Clean up old login attempts. Should be called periodically by a scheduled task.
     *
     * @return number of attempts removed
     */
    int cleanupOldAttempts();
}
