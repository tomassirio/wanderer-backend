package com.tomassirio.wanderer.commons.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;
import org.springframework.data.web.config.SpringDataWebSettings;

/**
 * Redis-based cache configuration for distributed caching across pods.
 *
 * <p>This replaces per-pod Caffeine caches with a centralized Redis cache, ensuring:
 *
 * <ul>
 *   <li>Cache consistency across multiple service instances
 *   <li>Reduced database load
 *   <li>Better cache hit rates with shared cache
 * </ul>
 *
 * <p><b>IMPORTANT:</b> After deploying changes to this configuration, you MUST clear the Redis
 * cache to prevent deserialization errors from incompatible cached data:
 * <pre>
 * kubectl exec -n wanderer-dev &lt;redis-pod-name&gt; -- redis-cli FLUSHDB
 * </pre>
 *
 * @since 1.1.0
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    private static final String CACHE_VERSION = "v1";

    public static final String USERS_CACHE = CACHE_VERSION + ":users";
    public static final String USER_BY_USERNAME_CACHE = CACHE_VERSION + ":usersByUsername";
    public static final String TRIPS_CACHE = CACHE_VERSION + ":trips";
    public static final String TRIP_UPDATES_CACHE = CACHE_VERSION + ":tripUpdates";
    public static final String TRIP_UPDATE_LOCATIONS_CACHE = CACHE_VERSION + ":tripUpdateLocations";
    public static final String PROMOTED_TRIPS_CACHE = CACHE_VERSION + ":promotedTrips";
    public static final String ACHIEVEMENTS_CACHE = CACHE_VERSION + ":achievements";
    public static final String USER_ACHIEVEMENTS_CACHE = CACHE_VERSION + ":userAchievements";
    public static final String FRIENDSHIPS_CACHE = CACHE_VERSION + ":friendships";
    public static final String FOLLOWERS_CACHE = CACHE_VERSION + ":followers";
    public static final String NOTIFICATIONS_COUNT_CACHE = CACHE_VERSION + ":notificationsCount";

    @Value("${cache.default.ttl-seconds:300}")
    private long defaultTtlSeconds;

    @Value("${cache.users.ttl-seconds:600}")
    private long usersTtl;

    @Value("${cache.trips.ttl-seconds:300}")
    private long tripsTtl;

    @Value("${cache.trip-updates.ttl-seconds:30}")
    private long tripUpdatesTtl;

    @Value("${cache.promoted-trips.ttl-seconds:600}")
    private long promotedTripsTtl;

    @Value("${cache.achievements.ttl-seconds:3600}")
    private long achievementsTtl;

    @Value("${cache.friendships.ttl-seconds:600}")
    private long friendshipsTtl;

    @Value("${cache.notifications-count.ttl-seconds:60}")
    private long notificationsCountTtl;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Register Java time module for LocalDateTime, etc.
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Configure deserialization to be lenient
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        // Register Spring Data module for Page serialization BEFORE findAndRegisterModules
        SpringDataWebSettings settings = new SpringDataWebSettings(
                EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO);
        objectMapper.registerModule(new SpringDataJacksonConfiguration.PageModule(settings));
        
        // Find and register other modules (after Spring Data module to avoid conflicts)
        objectMapper.findAndRegisterModules();

        // Use GenericJackson2JsonRedisSerializer which handles type information automatically
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new StringRedisSerializer()))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        serializer))
                        .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(
                        USERS_CACHE, defaultConfig.entryTtl(Duration.ofSeconds(usersTtl)))
                .withCacheConfiguration(
                        USER_BY_USERNAME_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(usersTtl)))
                .withCacheConfiguration(
                        TRIPS_CACHE, defaultConfig.entryTtl(Duration.ofSeconds(tripsTtl)))
                .withCacheConfiguration(
                        TRIP_UPDATES_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(tripUpdatesTtl)))
                .withCacheConfiguration(
                        TRIP_UPDATE_LOCATIONS_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(tripUpdatesTtl)))
                .withCacheConfiguration(
                        PROMOTED_TRIPS_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(promotedTripsTtl)))
                .withCacheConfiguration(
                        ACHIEVEMENTS_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(achievementsTtl)))
                .withCacheConfiguration(
                        USER_ACHIEVEMENTS_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(achievementsTtl)))
                .withCacheConfiguration(
                        FRIENDSHIPS_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(friendshipsTtl)))
                .withCacheConfiguration(
                        FOLLOWERS_CACHE, defaultConfig.entryTtl(Duration.ofSeconds(friendshipsTtl)))
                .withCacheConfiguration(
                        NOTIFICATIONS_COUNT_CACHE,
                        defaultConfig.entryTtl(Duration.ofSeconds(notificationsCountTtl)))
                .transactionAware()
                .build();
    }
}
