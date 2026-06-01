package com.energyanalyzer.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Configurable thresholds for the energy analyzer.
 *
 * Loaded from application.yml under the energy-analyzer.thresholds prefix.
 *
 * Different project types can use different thresholds:
 *   - Trading/financial systems: minimumEeiThreshold: 80
 *   - Batch processing systems: minimumEeiThreshold: 50
 *   - General web apps: minimumEeiThreshold: 60
 */
@Data
@Component
@ConfigurationProperties(prefix = "energy-analyzer.thresholds")
@Primary
public class ThresholdConfiguration {

    /**
     * Minimum acceptable EEI score for any method.
     * Build fails if ANY method scores below this.
     * Default: 60
     */
    private double minimumEeiThreshold = 60.0;

    /**
     * EEI score below which a method is classified as CRITICAL tier.
     * Default: 30
     */
    private double criticalThreshold = 30.0;

    /**
     * Maximum number of hotspot methods allowed before build failure.
     * Default: 5
     */
    private int maxAllowedHotspots = 5;

    /**
     * Whether to actually fail the build on threshold violation.
     * Set to false for "warn-only" mode (useful during migration periods).
     * Default: true
     */
    private boolean failBuildOnViolation = true;

    /**
     * Minimum EEI threshold for weighted EEI score.
     * Weighted EEI accounts for structural importance.
     */
    private double minimumWeightedEeiThreshold = 40.0;
}
