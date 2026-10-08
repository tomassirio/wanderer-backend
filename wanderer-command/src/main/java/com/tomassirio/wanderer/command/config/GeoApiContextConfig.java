package com.tomassirio.wanderer.command.config;

import com.google.maps.GeoApiContext;
import com.tomassirio.wanderer.command.config.properties.GoogleMapsProperties;
import com.tomassirio.wanderer.command.service.DistanceCalculationStrategy;
import com.tomassirio.wanderer.command.service.GeocodingService;
import com.tomassirio.wanderer.command.service.RouteService;
import com.tomassirio.wanderer.command.service.impl.GoogleGeocodingServiceImpl;
import com.tomassirio.wanderer.command.service.impl.NoOpGeocodingServiceImpl;
import com.tomassirio.wanderer.command.service.impl.strategy.GoogleDirectionsRouteStrategy;
import com.tomassirio.wanderer.command.service.impl.strategy.GoogleMapsDistanceStrategy;
import com.tomassirio.wanderer.command.service.impl.strategy.HaversineDistanceStrategy;
import com.tomassirio.wanderer.command.service.impl.strategy.StraightLineRouteStrategy;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that manages the shared {@link GeoApiContext} lifecycle and selects the
 * appropriate distance calculation strategy.
 *
 * <p>The {@link GeoApiContext} is the thread-safe entry point for all Google Maps API calls. This
 * configuration creates a single instance that is shared across all services that need Google Maps
 * access (e.g., distance calculation, route/polyline computation), avoiding duplicate connections
 * and ensuring proper shutdown.
 *
 * <p>If the API key is not configured or the feature is disabled, no context is created and
 * dependent services should fall back to offline alternatives.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GoogleMapsProperties.class)
public class GeoApiContextConfig {

    private GeoApiContext geoApiContext;

    /**
     * Creates a {@link GeoApiContext} bean if the Google Maps API key is configured and enabled.
     *
     * @param properties the Google Maps configuration properties
     * @return the configured {@link GeoApiContext}, or {@code null} if not configured
     */
    @Bean
    public GeoApiContext geoApiContext(GoogleMapsProperties properties) {
        if (properties.getApiKey() != null
                && !properties.getApiKey().isEmpty()
                && properties.isEnabled()) {
            // Bounded so a slow Google call can't pin a worker thread indefinitely.
            geoApiContext =
                    new GeoApiContext.Builder()
                            .apiKey(properties.getApiKey())
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .retryTimeout(10, TimeUnit.SECONDS)
                            .build();
            log.info("Google Maps GeoApiContext initialized successfully");
            return geoApiContext;
        }

        log.warn(
                "Google Maps API key not configured or disabled. Services will use offline"
                        + " fallbacks");
        return null;
    }

    /**
     * Selects the distance calculation strategy based on whether the Google Maps API is available.
     *
     * <p>When the API is configured, uses {@link GoogleMapsDistanceStrategy} with Haversine as a
     * runtime fallback for transient failures. Otherwise, uses {@link HaversineDistanceStrategy}
     * directly.
     *
     * @param geoApiContext the Google Maps API context (nullable)
     * @return the selected strategy
     */
    @Bean
    public DistanceCalculationStrategy distanceCalculationStrategy(GeoApiContext geoApiContext) {
        HaversineDistanceStrategy haversine = new HaversineDistanceStrategy();
        if (geoApiContext != null) {
            log.info("Using Google Maps Distance Matrix API for distance calculations");
            return new GoogleMapsDistanceStrategy(geoApiContext, haversine);
        }
        log.info("Using Haversine formula for distance calculations");
        return haversine;
    }

    /**
     * Selects the route service strategy based on whether the Google Maps API is available.
     *
     * <p>When the API is configured, uses {@link GoogleDirectionsRouteStrategy} with straight-line
     * as a runtime fallback for transient failures. Otherwise, uses {@link
     * StraightLineRouteStrategy} directly.
     *
     * @param geoApiContext the Google Maps API context (nullable)
     * @return the selected strategy
     */
    @Bean
    public RouteService routeService(GeoApiContext geoApiContext) {
        StraightLineRouteStrategy straightLine = new StraightLineRouteStrategy();
        if (geoApiContext != null) {
            log.info("Using Google Directions API for route computation");
            return new GoogleDirectionsRouteStrategy(geoApiContext, straightLine);
        }
        log.info("Using straight-line fallback for route computation");
        return straightLine;
    }

    /**
     * Selects the geocoding service based on whether the Google Maps API is available.
     *
     * <p>When the API is configured, uses {@link GoogleGeocodingServiceImpl} for reverse geocoding.
     * Otherwise, uses a no-op implementation that always returns {@code null}.
     *
     * @param geoApiContext the Google Maps API context (nullable)
     * @return the selected geocoding service
     */
    @Bean
    public GeocodingService geocodingService(GeoApiContext geoApiContext) {
        if (geoApiContext != null) {
            log.info("Using Google Geocoding API for reverse geocoding");
            return new GoogleGeocodingServiceImpl(geoApiContext);
        }
        log.info("Geocoding disabled — no API key configured");
        return new NoOpGeocodingServiceImpl();
    }

    @PreDestroy
    public void cleanup() {
        if (geoApiContext != null) {
            geoApiContext.shutdown();
            log.info("Google Maps GeoApiContext shut down");
        }
    }
}
