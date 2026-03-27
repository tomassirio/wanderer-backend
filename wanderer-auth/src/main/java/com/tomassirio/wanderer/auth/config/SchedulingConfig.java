package com.tomassirio.wanderer.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for enabling scheduled tasks.
 * Enables the scheduler for token cleanup and login attempt cleanup jobs.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
