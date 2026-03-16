package com.tomassirio.wanderer.query.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized cache specifications bound from {@code cache.specs[*]} properties.
 *
 * <p>Each entry describes a single named Caffeine cache with its TTL and maximum size. Adding a new
 * cache only requires a new property block — no code changes needed (Open/Closed Principle).
 *
 * <p>Example configuration in {@code application.properties}:
 *
 * <pre>
 * cache.specs[0].name=tripUpdates
 * cache.specs[0].ttl-seconds=30
 * cache.specs[0].max-size=500
 * </pre>
 *
 * @since 0.10.2
 */
@Data
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    private List<CacheSpec> specs = new ArrayList<>();

    /**
     * Specification for a single named Caffeine cache.
     *
     * @since 0.10.2
     */
    @Data
    public static class CacheSpec {

        /** Logical cache name referenced by {@code @Cacheable} annotations. */
        private String name;

        /** Time-to-live in seconds after the last write. Defaults to 60. */
        private long ttlSeconds = 60;

        /** Maximum number of entries the cache may hold. Defaults to 100. */
        private long maxSize = 100;
    }
}
