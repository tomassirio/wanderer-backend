package com.tomassirio.wanderer.query.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine-based cache configuration for the query module.
 *
 * <p>Cache definitions are driven entirely by {@link CacheProperties} — adding a new cache only
 * requires a new entry in {@code application.properties}, no code changes needed (Open/Closed
 * Principle).
 *
 * <p>Because command and query are separate microservices, cache invalidation on write is not
 * possible via Spring events. Instead, short TTL-based expiration is used to keep cached data
 * reasonably fresh while reducing database load for hot trips.
 *
 * @since 0.10.2
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
@RequiredArgsConstructor
public class CacheConfig {

    public static final String TRIP_UPDATES_CACHE = "tripUpdates";

    private final CacheProperties cacheProperties;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(cacheProperties.getSpecs().stream().map(this::buildCache).toList());
        return cacheManager;
    }

    private CaffeineCache buildCache(CacheProperties.CacheSpec spec) {
        return new CaffeineCache(
                spec.getName(),
                Caffeine.newBuilder()
                        .expireAfterWrite(spec.getTtlSeconds(), TimeUnit.SECONDS)
                        .maximumSize(spec.getMaxSize())
                        .recordStats()
                        .build());
    }
}
