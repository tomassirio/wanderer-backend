package com.tomassirio.wanderer.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for asynchronous processing in auth service.
 *
 * <p>Enables @Async annotation support for non-blocking operations like email sending.
 */
@Configuration
@EnableAsync
public class AsyncConfig {}
