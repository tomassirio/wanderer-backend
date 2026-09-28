package com.tomassirio.wanderer.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoConfig {}
