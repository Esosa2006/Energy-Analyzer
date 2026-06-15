package com.energyanalyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodAnalysisResult {

    // ── Identity ──────────────────────────────────────────────────────────────
    private String methodName;
    private String className;
    private String sourceFile;
    private int startLine;

    // ── Raw Static Metrics ────────────────────────────────────────────────────
    private StaticMetrics metrics;

    // ── Complexity Analysis ───────────────────────────────────────────────────
    private ComplexityClass complexityClass;
    private String relativeCost;

    // ── EEI Scoring ───────────────────────────────────────────────────────────
    private double eeiScore;
    private double weightedEei;
    private double weightMultiplier;

    // ── Tier & Graph Metrics ──────────────────────────────────────────────────
    private EnergyTier energyTier;
    private int fanIn;
    private int fanOut;
    private double centralityScore;
    private int callChainDepth;

    // ── Hotspot Detection ─────────────────────────────────────────────────────
    private boolean hotspot;

    @Builder.Default
    private List<String> hotspotReasons = new ArrayList<>();

    // ── Optimization Suggestions ──────────────────────────────────────────────
    @Builder.Default
    private List<OptimizationSuggestion> suggestions = new ArrayList<>();

    // ── Detected Anti-Patterns ────────────────────────────────────────────────
    @Builder.Default
    private List<String> antiPatterns = new ArrayList<>();

    // ── Helper Methods ────────────────────────────────────────────────────────

    public String getFullMethodId() {
        return className + "#" + methodName;
    }

    public String getEeiFormatted() {
        return String.format("%.1f", eeiScore);
    }

    public String getWeightedEeiFormatted() {
        return String.format("%.1f", weightedEei);
    }

    /**
     * Sum of estimatedEeiGain across all suggestions.
     * Avoids Java Stream lambdas in Thymeleaf SpEL.
     */
    public int getTotalSuggestionGain() {
        if (suggestions == null) return 0;
        int total = 0;
        for (OptimizationSuggestion s : suggestions) {
            total += s.getEstimatedEeiGain();
        }
        return total;
    }

    /**
     * Projected EEI if all suggestions are applied, capped at 100.
     * Avoids T(Math).min() in Thymeleaf SpEL (blocked by security ACL).
     */
    public double getProjectedEei() {
        double projected = eeiScore + getTotalSuggestionGain();
        return projected > 100.0 ? 100.0 : projected;
    }
}