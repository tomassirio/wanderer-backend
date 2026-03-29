package com.tomassirio.wanderer.commons.dto;

import java.util.UUID;

/**
 * Minimal user information for internal service communication.
 * Used by auth service to lookup users without full entity details.
 */
public record UserBasicInfo(UUID id, String username) {}
