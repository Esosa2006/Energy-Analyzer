package com.energyanalyzer.service;

import com.energyanalyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * HotspotDetectorService
 *
 * Flags methods as performance/efficiency hotspots based on multiple criteria.
 *
 * A HOTSPOT is a method that:
 *   - Has poor EEI AND is structurally important, OR
 *   - Has very poor EEI regardless of structure, OR
 *   - Combines multiple anti-patterns with high complexity
 *
 * Hotspot detection uses a multi-criteria scoring approach:
 *   Each criterion that matches adds to the hotspot score.
 *   If total score >= hotspotThreshold → flagged as hotspot.
 *
 * This avoids false positives from a single criterion while
 * catching truly problematic methods.
 */
@Slf4j
@Service
public class HotspotDetectorService {

    private static final double EEI_HOTSPOT_THRESHOLD = 55.0;
    private static final double WEIGHTED_EEI_IMPACT_THRESHOLD = 150.0;
    private static final int ANTI_PATTERN_COUNT_THRESHOLD = 2;
    private static final int HOTSPOT_SCORE_THRESHOLD = 2; // votes needed

    /**
     * Determine if a method is a hotspot and collect reasons.
     *
     * @param result The partially-built method analysis result
     * @return Whether this method is a hotspot
     */
    public boolean isHotspot(MethodAnalysisResult result) {
        return !collectHotspotReasons(result).isEmpty();
    }

    /**
     * Collect all reasons why this method is (or isn't) a hotspot.
     */
    public List<String> collectHotspotReasons(MethodAnalysisResult result) {
        List<String> reasons = new ArrayList<>();

        // ── Criterion 1: EEI below threshold ─────────────────────────────────
        if (result.getEeiScore() < EEI_HOTSPOT_THRESHOLD) {
            reasons.add(String.format("EEI score %.1f is below hotspot threshold %.1f",
                    result.getEeiScore(), EEI_HOTSPOT_THRESHOLD));
        }

        // ── Criterion 2: CRITICAL or HIGH energy tier ─────────────────────────
        if (result.getEnergyTier() == EnergyTier.CRITICAL) {
            reasons.add("Energy tier is CRITICAL - immediate optimization required");
        } else if (result.getEnergyTier() == EnergyTier.HIGH) {
            reasons.add("Energy tier is HIGH - optimization recommended");
        }

        // ── Criterion 3: Quadratic or worse complexity ─────────────────────────
        if (result.getComplexityClass() == ComplexityClass.O_N2
                || result.getComplexityClass() == ComplexityClass.O_N3
                || result.getComplexityClass() == ComplexityClass.O_2N) {
            reasons.add("Complexity class " + result.getComplexityClass().getLabel()
                    + " will degrade significantly with larger inputs");
        }

        // ── Criterion 4: Multiple anti-patterns ───────────────────────────────
        if (result.getAntiPatterns().size() >= ANTI_PATTERN_COUNT_THRESHOLD) {
            reasons.add(result.getAntiPatterns().size() + " anti-patterns detected: "
                    + String.join(", ", result.getAntiPatterns()));
        }

        // ── Criterion 5: High structural impact + poor EEI ────────────────────
        // A method called by many others is a hotspot even with moderately low EEI,
        // because its inefficiency is amplified across the codebase.
        double impactScore = (100.0 - result.getEeiScore()) * result.getWeightMultiplier();
        if (impactScore >= WEIGHTED_EEI_IMPACT_THRESHOLD) {
            reasons.add(String.format(
                "High structural impact: EEI %.1f × weight multiplier %.1fx = %.0f impact score",
                result.getEeiScore(), result.getWeightMultiplier(), impactScore));
        }

        // Only flag as hotspot if at least HOTSPOT_SCORE_THRESHOLD criteria are met
        // (reduces false positives from a single marginal criterion)
        if (reasons.size() < HOTSPOT_SCORE_THRESHOLD) {
            return new ArrayList<>(); // Not enough criteria → not a hotspot
        }

        return reasons;
    }
}
