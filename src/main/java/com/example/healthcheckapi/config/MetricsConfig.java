package com.example.healthcheckapi.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration for CloudWatch Agent integration
 * - Enables @Timed annotation support via TimedAspect
 * - Metrics are sent via StatsD protocol to CloudWatch Agent (localhost:8125)
 */
@Configuration
public class MetricsConfig {

    /**
     * Enable @Timed annotation for automatic API timing
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Add only application tag (keep metric names short)
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "csye6225");
    }
}