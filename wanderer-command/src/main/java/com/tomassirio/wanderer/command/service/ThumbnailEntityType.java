package com.tomassirio.wanderer.command.service;

import lombok.Getter;

/**
 * Enum representing the different types of entities that can have thumbnails.
 *
 * @author tomassirio
 * @since 0.10.5
 */
@Getter
public enum ThumbnailEntityType {
    /** Trip entity - actual trips with updates */
    TRIP("trips"),

    /** Trip plan entity - planned routes */
    TRIP_PLAN("plans"),

    /** User profile entity - user profile pictures */
    USER_PROFILE("profiles");

    /**
     * -- GETTER -- Gets the storage subdirectory for this entity type.
     *
     * @return the subdirectory name
     */
    private final String subdirectory;

    ThumbnailEntityType(String subdirectory) {
        this.subdirectory = subdirectory;
    }
}
