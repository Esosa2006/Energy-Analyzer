package com.energyanalyzer.service;

import com.energyanalyzer.model.ComplexityClass;
import com.energyanalyzer.model.StaticMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ComplexityClassifierService
 *
 * Determines the approximate Big-O complexity class of a method
 * based on static structural heuristics.
 *
 * ACADEMIC NOTE:
 * Exact Big-O inference from source code is undecidable in general
 * (Rice's Theorem). This service uses well-established structural
 * heuristics from static analysis literature, prioritizing
 * interpretability and correctness for common patterns.
 *
 * Classification hierarchy (checked in order of priority):
 *   1. Exponential → recursive methods with branching OR exponential indicators
 *   2. Cubic       → triply nested loops
 *   3. Quadratic   → doubly nested loops
 *   4. Linear      → any single loop or linear recursion
 *   5. Constant    → no loops and no recursion
 */
@Slf4j
@Service
public class ComplexityClassifierService {

    /**
     * Classify a method's approximate complexity class.
     *
     * @param metrics Raw static metrics for the method
     * @return Approximate complexity class
     */
    public ComplexityClass classify(StaticMetrics metrics) {
        // ── Rule 1: Exponential ─────────────────────────────────────────────
        // Recursive methods with branching OR combinatorial patterns.
        // Examples: naive Fibonacci, combinatorial search, tree traversals
        // without memoization.
        if (metrics.isRecursive() && indicatesExponential(metrics)) {
            log.debug("{}: classified O(2^n) - recursive with branching",
                    metrics.getMethodName());
            return ComplexityClass.O_2N;
        }

        // ── Rule 2: Cubic ───────────────────────────────────────────────────
        // Three or more levels of nested loops.
        // Example: matrix multiplication O(n³), Floyd-Warshall
        if (metrics.getNestedLoopDepth() >= 3) {
            log.debug("{}: classified O(n³) - nested loop depth >= 3",
                    metrics.getMethodName());
            return ComplexityClass.O_N3;
        }

        // ── Rule 3: Quadratic ───────────────────────────────────────────────
        // Two levels of nested loops.
        // Example: bubble sort O(n²), naive duplicate detection
        if (metrics.getNestedLoopDepth() >= 2) {
            log.debug("{}: classified O(n²) - nested loop depth == 2",
                    metrics.getMethodName());
            return ComplexityClass.O_N2;
        }

        // ── Rule 4: Linear ──────────────────────────────────────────────────
        // At least one loop, or simple (non-branching) recursion.
        // Example: linear search, summing a list
        if (metrics.getLoopCount() > 0 || metrics.isRecursive()) {
            log.debug("{}: classified O(n) - has loops or recursion",
                    metrics.getMethodName());
            return ComplexityClass.O_N;
        }

        // ── Rule 5: Constant ────────────────────────────────────────────────
        // No loops, no recursion.
        // Example: getters, setters, simple calculations
        log.debug("{}: classified O(1) - no loops or recursion",
                metrics.getMethodName());
        return ComplexityClass.O_1;
    }

    /**
     * Determine if a recursive method has exponential growth indicators.
     *
     * Heuristics for exponential complexity:
     *  - Multiple recursive calls (branching recursion) → at least 2 self-calls
     *  - High cyclomatic complexity combined with recursion
     *  - Located inside a loop (compound recursion)
     *
     * Example: naive Fibonacci calls itself twice → O(2^n)
     * Example: linear recursion (factorial) → O(n), not O(2^n)
     *
     * Note: This is a heuristic. Detecting exact branching factor from
     * static analysis alone is approximative by design.
     */
    private boolean indicatesExponential(StaticMetrics metrics) {
        // High cyclomatic complexity + recursion often indicates combinatorial logic
        boolean highComplexityRecursion = metrics.getCyclomaticComplexity() >= 5;

        // Multiple method calls within a recursive method suggest branching
        // (the method calls itself multiple times)
        boolean manyCallsInMethod = metrics.getMethodCallCount() >= 3;

        // Loops combined with recursion compound complexity
        boolean recursionInLoop = metrics.getLoopCount() > 0;

        return highComplexityRecursion || manyCallsInMethod || recursionInLoop;
    }

    /**
     * Get a human-readable explanation for why this complexity class was assigned.
     * Useful for dashboard display and academic defense.
     */
    public String getClassificationRationale(StaticMetrics metrics, ComplexityClass clazz) {
        return switch (clazz) {
            case O_2N -> String.format(
                "Recursive method with branching indicators: " +
                "%d recursive calls detected, cyclomatic complexity %d. " +
                "Branching recursion grows exponentially with input size.",
                metrics.getMethodCallCount(), metrics.getCyclomaticComplexity());
            case O_N3 -> String.format(
                "Triply nested loops detected (depth=%d). " +
                "Performance degrades cubically as n grows.",
                metrics.getNestedLoopDepth());
            case O_N2 -> String.format(
                "Doubly nested loops detected (depth=%d). " +
                "Performance degrades quadratically as n grows.",
                metrics.getNestedLoopDepth());
            case O_N -> String.format(
                "%d loop(s) detected, no nesting beyond depth 1. " +
                "Linear performance growth with input size.",
                metrics.getLoopCount());
            case O_1 -> "No loops or recursion detected. " +
                "Performance is constant regardless of input size.";
        };
    }
}
