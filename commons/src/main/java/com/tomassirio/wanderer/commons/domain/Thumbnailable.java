package com.tomassirio.wanderer.commons.domain;

import java.util.UUID;
import lombok.Getter;

/**
 * Interface for entities that have thumbnails stored in the NAS. Thumbnails are stored by entity ID
 * and retrieved dynamically.
 */
public interface Thumbnailable {
    UUID getId();

    /** Returns the thumbnail type for this entity. Used to construct the thumbnail URL path. */
    ThumbnailType getThumbnailType();

    /** Generates the thumbnail URL for this entity. Pattern: /thumbnails/{type}/{id}.png */
    default String getThumbnailUrl() {
        if (getId() == null) {
            return null;
        }
        return "/thumbnails/" + getThumbnailType().getPath() + "/" + getId() + ".png";
    }

    @Getter
    enum ThumbnailType {
        TRIP("trips"),
        TRIP_PLAN("plans"),
        USER_PROFILE("profiles");

        private final String path;

        ThumbnailType(String path) {
            this.path = path;
        }
    }
}
