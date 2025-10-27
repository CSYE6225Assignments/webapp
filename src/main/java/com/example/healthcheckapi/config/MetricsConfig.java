package com.example.healthcheckapi.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration for CloudWatch Agent integration
 * - Enables @Timed annotation support via TimedAspect
 * - Adds common tags to all metrics
 * - Metrics are sent via StatsD protocol to CloudWatch Agent (localhost:8125)
 */
@Configuration
public class MetricsConfig {

    @Value("${spring.application.name:csye6225}")
    private String applicationName;

    @Value("${ENVIRONMENT:dev}")
    private String environment;

    /**
     * Enable @Timed annotation for automatic API timing
     * This allows us to use @Timed on controller methods
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Add common tags to all metrics
     * This helps filter and group metrics in CloudWatch
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                        "application", applicationName,
                        "environment", environment
                );
    }
}