package com.tomassirio.wanderer.auth.service;

import com.tomassirio.wanderer.commons.domain.User;

/**
 * Creates a Wanderer user across services: domain user in command, read back from query, and a
 * credential in the auth DB. Compensates by deleting the domain user if a later step fails.
 */
public interface UserProvisioningService {

    /**
     * Password registration path.
     *
     * @throws IllegalArgumentException if passwordHash is null or blank
     * @throws IllegalStateException if any step fails (after rollback)
     */
    User provisionWithPassword(
            String username, String email, String displayName, String passwordHash);

    /**
     * SSO path. The credential has no password.
     *
     * @throws IllegalStateException if any step fails (after rollback)
     */
    User provisionWithoutPassword(String username, String email, String displayName);
}
