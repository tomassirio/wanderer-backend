package com.tomassirio.wanderer.commons.dto;

import com.tomassirio.wanderer.commons.domain.Release;
import java.time.Instant;
import java.util.List;

/**
 * Release notes as returned by the API. See {@code docs/release-changelog-api.md}.
 *
 * @since 1.3.0
 */
public record ReleaseDTO(
        String id,
        String version,
        Release.Status status,
        String headline,
        boolean showPopup,
        List<Release.PlatformRelease> platforms,
        List<Release.Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public static ReleaseDTO from(Release r) {
        return new ReleaseDTO(
                r.getId().toString(),
                r.getVersion(),
                r.getStatus(),
                r.getHeadline(),
                r.isShowPopup(),
                List.copyOf(r.getPlatforms()),
                List.copyOf(r.getItems()),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
