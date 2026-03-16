package com.tomassirio.wanderer.query.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;

/**
 * Unit tests for {@link CacheConfig} verifying that named caches are registered correctly from
 * {@link CacheProperties} specifications.
 *
 * @since 0.10.2
 */
class CacheConfigTest {

    @Test
    void cacheManager_shouldRegisterTripUpdatesCache() {
        CacheManager cacheManager = createConfigWithDefaults();

        Cache cache = cacheManager.getCache(CacheConfig.TRIP_UPDATES_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("tripUpdates");
    }

    @Test
    void cacheManager_shouldRegisterTripUpdateLocationsCache() {
        CacheManager cacheManager = createConfigWithDefaults();

        Cache cache = cacheManager.getCache(CacheConfig.TRIP_UPDATE_LOCATIONS_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("tripUpdateLocations");
    }

    @Test
    void cacheManager_shouldContainBothNamedCaches() {
        CacheManager cacheManager = createConfigWithDefaults();

        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        CacheConfig.TRIP_UPDATES_CACHE, CacheConfig.TRIP_UPDATE_LOCATIONS_CACHE);
    }

    @Test
    void cacheManager_shouldStoreAndRetrieveValues() {
        CacheManager cacheManager = createConfigWithDefaults();
        Cache cache = cacheManager.getCache(CacheConfig.TRIP_UPDATES_CACHE);
        assertThat(cache).isNotNull();

        cache.put("testKey", "testValue");

        assertThat(cache.get("testKey")).isNotNull();
        assertThat(cache.get("testKey", String.class)).isEqualTo("testValue");
    }

    @Test
    void cacheManager_shouldHandleEmptySpecs() {
        CacheProperties properties = new CacheProperties();
        CacheConfig config = new CacheConfig(properties);

        CacheManager cacheManager = config.cacheManager();
        ((SimpleCacheManager) cacheManager).afterPropertiesSet();

        assertThat(cacheManager.getCacheNames()).isEmpty();
    }

    private CacheManager createConfigWithDefaults() {
        CacheProperties properties = new CacheProperties();
        properties.setSpecs(
                List.of(spec("tripUpdates", 30, 500), spec("tripUpdateLocations", 60, 200)));

        CacheConfig config = new CacheConfig(properties);
        CacheManager cacheManager = config.cacheManager();
        ((SimpleCacheManager) cacheManager).afterPropertiesSet();
        return cacheManager;
    }

    private static CacheProperties.CacheSpec spec(String name, long ttlSeconds, long maxSize) {
        CacheProperties.CacheSpec cacheSpec = new CacheProperties.CacheSpec();
        cacheSpec.setName(name);
        cacheSpec.setTtlSeconds(ttlSeconds);
        cacheSpec.setMaxSize(maxSize);
        return cacheSpec;
    }
}
