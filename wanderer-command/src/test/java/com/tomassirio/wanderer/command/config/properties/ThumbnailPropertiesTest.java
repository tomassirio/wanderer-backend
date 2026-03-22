package com.tomassirio.wanderer.command.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ThumbnailProperties}. */
class ThumbnailPropertiesTest {

    @Test
    void defaultValues_shouldBeSetCorrectly() {
        // When
        ThumbnailProperties properties = new ThumbnailProperties();

        // Then
        assertThat(properties.getStoragePath()).isEqualTo("/data/thumbnails");
        assertThat(properties.getWidth()).isEqualTo(600);
        assertThat(properties.getHeight()).isEqualTo(338);
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void setStoragePath_shouldUpdateValue() {
        // Given
        ThumbnailProperties properties = new ThumbnailProperties();
        String newPath = "/custom/path/thumbnails";

        // When
        properties.setStoragePath(newPath);

        // Then
        assertThat(properties.getStoragePath()).isEqualTo(newPath);
    }

    @Test
    void setWidth_shouldUpdateValue() {
        // Given
        ThumbnailProperties properties = new ThumbnailProperties();
        int newWidth = 800;

        // When
        properties.setWidth(newWidth);

        // Then
        assertThat(properties.getWidth()).isEqualTo(newWidth);
    }

    @Test
    void setHeight_shouldUpdateValue() {
        // Given
        ThumbnailProperties properties = new ThumbnailProperties();
        int newHeight = 450;

        // When
        properties.setHeight(newHeight);

        // Then
        assertThat(properties.getHeight()).isEqualTo(newHeight);
    }

    @Test
    void setEnabled_shouldUpdateValue() {
        // Given
        ThumbnailProperties properties = new ThumbnailProperties();

        // When
        properties.setEnabled(false);

        // Then
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void enabled_whenSetToTrue_shouldReturnTrue() {
        // Given
        ThumbnailProperties properties = new ThumbnailProperties();

        // When
        properties.setEnabled(true);

        // Then
        assertThat(properties.isEnabled()).isTrue();
    }
}
