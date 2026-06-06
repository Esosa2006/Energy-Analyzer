package com.energyanalyzer.service;

import com.energyanalyzer.model.ComplexityClass;
import com.energyanalyzer.model.StaticMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * EEICalculatorService - Energy Efficiency Index Calculator
 * Computes the EEI score (0–100) for a method.
 * ─────────────────────────────────────────────────────────────────
 * IMPORTANT ACADEMIC DISCLAIMER:
 * The EEI is NOT a measurement of actual energy consumption in Joules.
 * It is a RELATIVE SOFTWARE EFFICIENCY INDICATOR derived from static
 * analysis, correlating with known performance anti-patterns.
 * The theoretical grounding:
 *   Energy ≈ Power × Time
 *   Execution time is dominated by algorithmic complexity and anti-patterns.
 *   Therefore: higher complexity + more anti-patterns → relatively higher
 *   energy footprint at scale.
 * This approach is consistent with:
 *   - Saborido et al. (2014) "Evolutionary Design of Energy Efficient Java Software"
 *   - Pereira et al. (2017) "Energy Efficiency across Programming Languages"
 *   - Sahin et al. (2012) "How Do Code Refactorings Affect Energy Usage?"
 * ─────────────────────────────────────────────────────────────────
 * Scoring formula:
 *   1. Start with base score from complexity class
 *   2. Apply penalties for each detected anti-pattern
 *   3. Clamp result to [0, 100]
 */
@Slf4j
@Service
public class EEICalculatorService {

    // ── Penalty Constants ──────────────────────────────────────────────────────
    // Each represents how much an anti-pattern reduces the EEI score.
    // Values are calibrated to keep scores interpretable (0=worst, 100=best).

    private static final double RECURSION_PENALTY = 10.0;
    private static final double STRING_CONCAT_LOOP_PENALTY_PER_OCCURRENCE = 5.0;
    private static final double EXCESSIVE_OBJECT_CREATION_PENALTY = 3.0;
    private static final double IO_OPERATION_PENALTY_PER_OP = 8.0;
    private static final double HIGH_CYCLOMATIC_COMPLEXITY_PENALTY = 5.0;
    private static final double DEEP_NESTING_PENALTY = 7.0;
    private static final double EXCESSIVE_METHOD_CALLS_PENALTY = 2.0;

    // Thresholds that trigger penalties
    private static final int HIGH_CC_THRESHOLD = 10;
    private static final int HIGH_OBJECT_CREATION_THRESHOLD = 5;
    private static final int EXCESSIVE_IO_THRESHOLD = 3;
    private static final int EXCESSIVE_METHOD_CALLS_THRESHOLD = 15;

    /**
     * Calculate the EEI score for a method.
     *
     * @param metrics     Raw static metrics for the method
     * @param complexity  Classified complexity class
     * @return EEI score clamped to [0.0, 100.0]
     */
    public double calculate(StaticMetrics metrics, ComplexityClass complexity) {
        double score = complexity.getBaseEei();
        List<String> appliedPenalties = new ArrayList<>();

        // ── Penalty 1: Recursion ──────────────────────────────────────────────
        // Recursive methods risk stack overflow and often indicate a
        // non-optimal algorithmic approach when iteration suffices.
        if (metrics.isRecursive()) {
            score -= RECURSION_PENALTY;
            appliedPenalties.add(String.format("Recursion: -%.0f", RECURSION_PENALTY));
        }

        // ── Penalty 2: String Concatenation ───────────────────────────────────
        // String + in loops: each concat creates a new char[] copy.
        // For n iterations: O(1+2+...+n) = O(n²) memory operations.
        if (metrics.getStringConcatenationCount() > 0 && metrics.getLoopCount() > 0) {
            double penalty = metrics.getStringConcatenationCount()
                           * STRING_CONCAT_LOOP_PENALTY_PER_OCCURRENCE;
            score -= penalty;
            appliedPenalties.add(String.format("String concat in loops: -%.0f", penalty));
        }

        // ── Penalty 3: Excessive Object Creation ──────────────────────────────
        // Excessive 'new' allocations increase GC pressure and heap churn,
        // leading to more frequent pauses and higher energy usage.
        if (metrics.getObjectCreationCount() > HIGH_OBJECT_CREATION_THRESHOLD) {
            double penalty = EXCESSIVE_OBJECT_CREATION_PENALTY
                           * (metrics.getObjectCreationCount() - HIGH_OBJECT_CREATION_THRESHOLD);
            score -= penalty;
            appliedPenalties.add(String.format("Excessive object creation: -%.0f", penalty));
        }

        // ── Penalty 4: I/O Operations ─────────────────────────────────────────
        // I/O is typically 3–6 orders of magnitude slower than in-memory ops.
        // File/network/DB I/O without buffering is a major energy drain.
        if (metrics.getIoOperationCount() > EXCESSIVE_IO_THRESHOLD) {
            double penalty = IO_OPERATION_PENALTY_PER_OP
                           * (metrics.getIoOperationCount() - EXCESSIVE_IO_THRESHOLD);
            score -= penalty;
            appliedPenalties.add(String.format("Excessive I/O: -%.0f", penalty));
        }

        // ── Penalty 5: High Cyclomatic Complexity ─────────────────────────────
        // High CC means many execution paths → harder to optimize →
        // less predictable branch prediction → higher energy variability.
        if (metrics.getCyclomaticComplexity() > HIGH_CC_THRESHOLD) {
            double penalty = HIGH_CYCLOMATIC_COMPLEXITY_PENALTY
                           * (metrics.getCyclomaticComplexity() - HIGH_CC_THRESHOLD);
            score -= penalty;
            appliedPenalties.add(String.format("High cyclomatic complexity (%d): -%.0f",
                    metrics.getCyclomaticComplexity(), penalty));
        }

        // ── Penalty 6: Deep Nesting ───────────────────────────────────────────
        // Deeply nested loops (beyond what complexity class already penalizes)
        // are harder to cache-optimize and harder for compilers to vectorize.
        if (metrics.getNestedLoopDepth() >= 3) {
            score -= DEEP_NESTING_PENALTY;
            appliedPenalties.add(String.format("Deep nesting (depth=%d): -%.0f",
                    metrics.getNestedLoopDepth(), DEEP_NESTING_PENALTY));
        }

        // ── Penalty 7: Excessive Method Calls ────────────────────────────────
        // Very high method call count may indicate over-delegation, creating
        // stack frame overhead and reducing JIT optimization opportunities.
        if (metrics.getMethodCallCount() > EXCESSIVE_METHOD_CALLS_THRESHOLD) {
            score -= EXCESSIVE_METHOD_CALLS_PENALTY;
            appliedPenalties.add(String.format("Excessive method calls (%d): -%.0f",
                    metrics.getMethodCallCount(), EXCESSIVE_METHOD_CALLS_PENALTY));
        }

        // ── Clamp to valid range ──────────────────────────────────────────────
        double finalScore = Math.max(0.0, Math.min(100.0, score));

        log.debug("EEI for {}: base={}, penalties={}, final={}",
                metrics.getMethodName(),
                complexity.getBaseEei(),
                appliedPenalties,
                String.format("%.1f", finalScore));

        return finalScore;
    }

    /**
     * Identify which anti-patterns were detected in a method.
     * Used for the suggestions engine and hotspot detection.
     */
    public List<String> detectAntiPatterns(StaticMetrics metrics, ComplexityClass complexity) {
        List<String> antiPatterns = new ArrayList<>();

        if (metrics.isRecursive()) {
            antiPatterns.add("Recursion detected");
        }
        if (metrics.getStringConcatenationCount() > 0 && metrics.getLoopCount() > 0) {
            antiPatterns.add("String concatenation inside loop(s)");
        }
        if (metrics.getObjectCreationCount() > HIGH_OBJECT_CREATION_THRESHOLD) {
            antiPatterns.add("Excessive object creation (" + metrics.getObjectCreationCount() + " instances)");
        }
        if (metrics.getIoOperationCount() > EXCESSIVE_IO_THRESHOLD) {
            antiPatterns.add("High I/O operation count (" + metrics.getIoOperationCount() + ")");
        }
        if (metrics.getCyclomaticComplexity() > HIGH_CC_THRESHOLD) {
            antiPatterns.add("High cyclomatic complexity (" + metrics.getCyclomaticComplexity() + ")");
        }
        if (metrics.getNestedLoopDepth() >= 3) {
            antiPatterns.add("Deeply nested loops (depth " + metrics.getNestedLoopDepth() + ")");
        }
        if (complexity == ComplexityClass.O_N2 || complexity == ComplexityClass.O_N3) {
            antiPatterns.add("Polynomial complexity (" + complexity.getLabel() + ")");
        }
        if (complexity == ComplexityClass.O_2N) {
            antiPatterns.add("Exponential complexity - critical risk");
        }

        return antiPatterns;
    }
}
