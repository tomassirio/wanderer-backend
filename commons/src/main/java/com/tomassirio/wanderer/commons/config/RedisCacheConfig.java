package com.tomassirio.wanderer.commons.config;

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

/**
 * Redis-based cache configuration for distributed caching across pods.
 * 
 * <p>This replaces per-pod Caffeine caches with a centralized Redis cache, ensuring:
 * <ul>
 *   <li>Cache consistency across multiple service instances
 *   <li>Reduced database load
 *   <li>Better cache hit rates with shared cache
 * </ul>
 *
 * @since 1.1.0
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    public static final String USERS_CACHE = "users";
    public static final String USER_BY_USERNAME_CACHE = "usersByUsername";
    public static final String TRIPS_CACHE = "trips";
    public static final String TRIP_UPDATES_CACHE = "tripUpdates";
    public static final String TRIP_UPDATE_LOCATIONS_CACHE = "tripUpdateLocations";
    public static final String PROMOTED_TRIPS_CACHE = "promotedTrips";
    public static final String ACHIEVEMENTS_CACHE = "achievements";
    public static final String USER_ACHIEVEMENTS_CACHE = "userAchievements";
    public static final String FRIENDSHIPS_CACHE = "friendships";
    public static final String FOLLOWERS_CACHE = "followers";
    public static final String NOTIFICATIONS_COUNT_CACHE = "notificationsCount";

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
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(), 
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer serializer = 
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(USERS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(usersTtl)))
                .withCacheConfiguration(USER_BY_USERNAME_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(usersTtl)))
                .withCacheConfiguration(TRIPS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(tripsTtl)))
                .withCacheConfiguration(TRIP_UPDATES_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(tripUpdatesTtl)))
                .withCacheConfiguration(TRIP_UPDATE_LOCATIONS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(tripUpdatesTtl)))
                .withCacheConfiguration(PROMOTED_TRIPS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(promotedTripsTtl)))
                .withCacheConfiguration(ACHIEVEMENTS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(achievementsTtl)))
                .withCacheConfiguration(USER_ACHIEVEMENTS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(achievementsTtl)))
                .withCacheConfiguration(FRIENDSHIPS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(friendshipsTtl)))
                .withCacheConfiguration(FOLLOWERS_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(friendshipsTtl)))
                .withCacheConfiguration(NOTIFICATIONS_COUNT_CACHE, 
                        defaultConfig.entryTtl(Duration.ofSeconds(notificationsCountTtl)))
                .transactionAware()
                .build();
    }
}
