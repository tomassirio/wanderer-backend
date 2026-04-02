package com.tomassirio.wanderer.query.dto;

import java.util.UUID;

/**
 * DTO for user search results with basic information.
 *
 * @since 1.2.0
 */
public record UserSearchResult(UUID id, String username, String displayName, String avatarUrl) {}
