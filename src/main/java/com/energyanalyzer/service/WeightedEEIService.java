package com.energyanalyzer.service;

import com.energyanalyzer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * WeightedEEIService
 *
 * Computes the Weighted EEI score that accounts for structural importance.
 *
 * Formula:
 *   WeightedEEI = EEI × WeightMultiplier
 *
 * The WeightMultiplier is derived from the call graph:
 *   - high fan-in → method called by many → inefficiency amplified
 *   - deep call chain → cascading performance effects
 *   - high centrality → structurally pivotal in the system
 *
 * PURPOSE: Prioritize optimization effort.
 *
 * Example:
 *   Method A: EEI=45, called by 1 method  → WeightedEEI = 45×1.0 = 45
 *   Method B: EEI=45, called by 20 methods → WeightedEEI = 45×2.8 = 126
 *
 * Method B should be fixed first because its inefficiency is multiplied
 * across the entire codebase.
 *
 * NOTE: WeightedEEI can exceed 100. It is a priority score, not a cap-bounded index.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeightedEEIService {

    public double computeWeightedEei(double eeiScore, double weightMultiplier) {
        // Invert EEI for weighting: lower EEI = more inefficient = higher impact when central
        // WeightedImpact = (100 - EEI) × weightMultiplier → higher = more urgent
        // But we report as: how much effective inefficiency this method contributes
        double weightedEei = eeiScore * weightMultiplier;
        log.debug("WeightedEEI: {} × {} = {}", eeiScore, weightMultiplier,
                String.format("%.1f", weightedEei));
        return weightedEei;
    }

    /**
     * Compute the "weighted impact score" - how urgently this method needs attention.
     * Higher = more urgent. Used for hotspot sorting.
     */
    public double computeImpactScore(double eeiScore, double weightMultiplier) {
        return (100.0 - eeiScore) * weightMultiplier;
    }
}
