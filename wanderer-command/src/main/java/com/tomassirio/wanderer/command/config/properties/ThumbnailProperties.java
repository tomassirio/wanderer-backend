package com.tomassirio.wanderer.command.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for thumbnail storage.
 *
 * <p>Binds to properties with the prefix "thumbnail".
 */
@ConfigurationProperties(prefix = "thumbnail")
@Data
@Validated
public class ThumbnailProperties {

    /**
     * Local filesystem path where thumbnails will be stored. Defaults to /data/thumbnails for
     * Kubernetes PersistentVolume. Subdirectories (trips/, plans/, profiles/) are created
     * automatically.
     */
    private String storagePath = "/data/thumbnails";

    /** Width of generated thumbnails in pixels. */
    private int width = 600;

    /** Height of generated thumbnails in pixels. */
    private int height = 338;

    /** Whether thumbnail generation is enabled. */
    private boolean enabled = true;
}
