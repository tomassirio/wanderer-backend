package com.tomassirio.wanderer.commons.dto;

import java.util.List;

/**
 * Unread "What's new" releases for the current user.
 *
 * @param lastSeenVersion stored last-seen version, {@code null} if the user has none yet
 * @param releases unread releases, newest version first
 * @since 1.3.0
 */
public record UnreadReleasesDTO(String lastSeenVersion, List<ReleaseDTO> releases) {}
