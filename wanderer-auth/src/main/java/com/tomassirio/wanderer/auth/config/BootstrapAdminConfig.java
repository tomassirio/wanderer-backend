package com.tomassirio.wanderer.auth.config;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.service.UserRoleService;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bootstrap component that handles initial admin user setup. If no admin users exist in the system,
 * this component can automatically promote a configured user to admin role.
 *
 * <p>Configuration:
 *
 * <ul>
 *   <li>bootstrap.admin.username - Username of user to promote to admin (optional)
 *   <li>bootstrap.admin.enabled - Enable/disable auto-promotion (default: true)
 * </ul>
 *
 * @since 0.5.2
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminConfig {

    private final UserRoleService userRoleService;
    private final WandererQueryClient wandererQueryClient;

    @Value("${bootstrap.admin.username:}")
    private String bootstrapAdminUsername;

    @Value("${bootstrap.admin.enabled:true}")
    private boolean bootstrapEnabled;

    /**
     * Handles the bootstrap process after application is ready. If no admins exist and a bootstrap
     * username is configured, promotes that user to admin.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!bootstrapEnabled) {
            log.info("Bootstrap admin promotion is disabled");
            return;
        }

        try {
            boolean hasAdmins = userRoleService.hasAnyAdmins();

            if (hasAdmins) {
                log.info("Admin users already exist in the system");
                return;
            }

            if (bootstrapAdminUsername == null || bootstrapAdminUsername.trim().isEmpty()) {
                log.warn(
                        "No admin users exist and no bootstrap admin username configured. "
                                + "Set bootstrap.admin.username to auto-promote first admin.");
                return;
            }

            log.info(
                    "No admin users found. Attempting to bootstrap admin user: {}",
                    bootstrapAdminUsername);

            User user;
            try {
                UserBasicInfo userInfo =
                        wandererQueryClient.getUserByUsername(bootstrapAdminUsername, "basic");
                user = new User();
                user.setId(userInfo.id());
                user.setUsername(userInfo.username());
            } catch (FeignException e) {
                if (e.status() == 404) {
                    log.error(
                            "Bootstrap admin user '{}' not found. "
                                    + "Create the user first, then restart the application.",
                            bootstrapAdminUsername);
                    return;
                }
                throw new RuntimeException("Failed to fetch bootstrap admin user", e);
            }

            userRoleService.promoteToAdmin(user.getId());
            log.info(
                    "Successfully promoted user '{}' to admin as first admin user",
                    bootstrapAdminUsername);

        } catch (Exception e) {
            log.error("Failed to bootstrap admin user", e);
            // Don't fail application startup
        }
    }
}
