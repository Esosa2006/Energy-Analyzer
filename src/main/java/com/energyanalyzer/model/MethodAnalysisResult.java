package com.energyanalyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete analysis result for a single Java method.
 *
 * Aggregates all data from the analysis pipeline:
 *   StaticMetrics → ComplexityClass → EEI → WeightedEEI → Tier → Hotspot → Suggestions
 */
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

    /** Approximate algorithmic complexity class */
    private ComplexityClass complexityClass;

    /** Relative energy cost label (e.g. "100x") */
    private String relativeCost;

    // ── EEI Scoring ───────────────────────────────────────────────────────────

    /**
     * Energy Efficiency Index (0–100).
     *
     * NOT actual energy in Joules. A relative software efficiency indicator.
     * Lower = more likely to consume disproportionate resources at scale.
     */
    private double eeiScore;

    /**
     * Weighted EEI = EEI × weightMultiplier
     *
     * Accounts for structural importance: an inefficient method called by
     * many others has greater system-wide impact than an isolated helper.
     */
    private double weightedEei;

    /** Weight multiplier derived from call graph centrality */
    private double weightMultiplier;

    // ── Tier & Graph Metrics ──────────────────────────────────────────────────

    private EnergyTier energyTier;

    /** Fan-in: how many methods call this one */
    private int fanIn;

    /** Fan-out: how many methods this one calls */
    private int fanOut;

    /** Structural centrality score (0.0–1.0) */
    private double centralityScore;

    /** Call chain depth from this method */
    private int callChainDepth;

    // ── Hotspot Detection ─────────────────────────────────────────────────────

    /** True if this method is flagged as a performance/efficiency hotspot */
    private boolean hotspot;

    /** Reasons why this method was flagged as a hotspot */
    @Builder.Default
    private List<String> hotspotReasons = new ArrayList<>();

    // ── Optimization Suggestions ──────────────────────────────────────────────

    /** Rule-based optimization suggestions with explanations */
    @Builder.Default
    private List<OptimizationSuggestion> suggestions = new ArrayList<>();

    // ── Detected Anti-Patterns ────────────────────────────────────────────────

    /** List of detected anti-patterns (e.g. "String concat in loop") */
    @Builder.Default
    private List<String> antiPatterns = new ArrayList<>();

    // ── Helper Methods ────────────────────────────────────────────────────────

    /** Unique display identifier */
    public String getFullMethodId() {
        return className + "#" + methodName;
    }

    /** EEI formatted to 1 decimal place */
    public String getEeiFormatted() {
        return String.format("%.1f", eeiScore);
    }

    /** Weighted EEI formatted to 1 decimal place */
    public String getWeightedEeiFormatted() {
        return String.format("%.1f", weightedEei);
    }
}
