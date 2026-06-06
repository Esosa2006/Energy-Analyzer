package com.energyanalyzer.service;

import com.energyanalyzer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * ThresholdValidationService
 * Validates analysis results against configured quality thresholds.
 * Determines whether the build should pass or fail.
 * This simulates a production-grade CI/CD quality gate, similar to:
 *   - SonarQube Quality Gates
 *   - PMD build-breakers
 *   - Checkstyle enforcement
 * Configuration is loaded from application.yml:
 *   energy-analyzer.thresholds.*
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdValidationService {

    private final ThresholdConfiguration thresholds;

    /**
     * Validate all method results against configured thresholds.
     *
     * @param results  All method analysis results
     * @return List of violation messages (empty = build passed)
     */
    public List<String> validate(List<MethodAnalysisResult> results) {
        List<String> violations = new ArrayList<>();

        for (MethodAnalysisResult result : results) {

            // ── Check 1: Minimum EEI threshold ────────────────────────────
            if (result.getEeiScore() < thresholds.getMinimumEeiThreshold()) {
                violations.add(String.format(
                    "BUILD VIOLATION: Method %s has EEI score %.1f, below minimum threshold %.1f",
                    result.getFullMethodId(),
                    result.getEeiScore(),
                    thresholds.getMinimumEeiThreshold()));
            }

            // ── Check 2: Critical threshold ────────────────────────────────
            if (result.getEeiScore() < thresholds.getCriticalThreshold()) {
                violations.add(String.format(
                    "CRITICAL VIOLATION: Method %s has EEI score %.1f, below critical threshold %.1f. " +
                    "This method poses serious performance risk.",
                    result.getFullMethodId(),
                    result.getEeiScore(),
                    thresholds.getCriticalThreshold()));
            }
        }

        // ── Check 3: Total hotspot count ───────────────────────────────────
        long hotspotCount = results.stream().filter(MethodAnalysisResult::isHotspot).count();
        if (hotspotCount > thresholds.getMaxAllowedHotspots()) {
            violations.add(String.format(
                "BUILD VIOLATION: %d hotspot methods detected, exceeding maximum allowed (%d). " +
                "Review and optimize hotspot methods before merging.",
                hotspotCount, thresholds.getMaxAllowedHotspots()));
        }

        if (!violations.isEmpty()) {
            log.warn("Threshold validation FAILED with {} violations", violations.size());
        } else {
            log.info("Threshold validation PASSED - all metrics within configured thresholds");
        }

        return violations;
    }

    /**
     * Determine if the build should fail based on violations and configuration.
     */
    public boolean shouldFailBuild(List<String> violations) {
        return !violations.isEmpty() && thresholds.isFailBuildOnViolation();
    }
}
