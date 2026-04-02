package com.tomassirio.wanderer.commons.config;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Redis metrics configuration for Prometheus. */
@Configuration
@ConditionalOnProperty(
        value = "management.metrics.redis.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RedisMetricsConfig {

    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources(MeterRegistry meterRegistry) {
        return ClientResources.builder()
                .commandLatencyRecorder(
                        new MicrometerCommandLatencyRecorder(
                                meterRegistry, MicrometerOptions.create()))
                .build();
    }
}
