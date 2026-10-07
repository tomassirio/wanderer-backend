package com.tomassirio.wanderer.commons.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A release of the apps, with the release notes shown in the "What's new" popup and history.
 *
 * <p>A release is visible on a platform once it is {@link Status#PUBLISHED} and that platform's
 * release date has passed. See {@code docs/release-changelog-api.md}.
 *
 * @since 1.3.0
 */
@Entity
@Table(name = "releases")
@Getter
@Setter
@NoArgsConstructor
public class Release {

    public static final int MAX_TITLE = 200;
    public static final int MAX_TEXT = 500;
    private static final Pattern VERSION = Pattern.compile("\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}");

    public enum Status {
        DRAFT,
        PUBLISHED
    }

    public enum Platform {
        ANDROID,
        WEB;

        /** Case-insensitive parse; throws {@link IllegalArgumentException} (400) on bad input. */
        public static Platform parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Invalid platform: " + value + " (expected ANDROID or WEB)");
            }
        }
    }

    public enum ItemType {
        NEW,
        IMPROVED,
        FIXED
    }

    /** Release date of this release on one platform. {@code null} = not released there yet. */
    @Embeddable
    public record PlatformRelease(
            @Enumerated(EnumType.STRING) @NotNull Platform platform, Instant releaseDate) {}

    /** One line of the release notes. */
    @Embeddable
    public record Item(
            @Enumerated(EnumType.STRING) @NotNull ItemType type,
            @NotBlank @Size(max = MAX_TITLE) String title,
            @Size(max = MAX_TEXT) String text,
            Integer prNumber) {}

    @Id private UUID id;

    @Column(nullable = false, unique = true)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String headline;

    @Column(name = "show_popup", nullable = false)
    private boolean showPopup = true;

    @ElementCollection
    @CollectionTable(name = "release_platforms", joinColumns = @JoinColumn(name = "release_id"))
    private List<PlatformRelease> platforms = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "release_items", joinColumns = @JoinColumn(name = "release_id"))
    @OrderColumn(name = "position")
    private List<Item> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Validates a {@code MAJOR.MINOR.PATCH} version; throws {@link IllegalArgumentException}. */
    public static String requireVersion(String version) {
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "Invalid version: " + version + " (expected MAJOR.MINOR.PATCH)");
        }
        return version;
    }

    /** Numeric semver comparison, so {@code 1.10.0 > 1.9.0}. */
    public static int compareVersions(String a, String b) {
        return Arrays.compare(parts(a), parts(b));
    }

    private static int[] parts(String version) {
        return Arrays.stream(requireVersion(version).split("\\."))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
