package com.energyanalyzer.model;

import lombok.Getter;

/**
 * Approximate algorithmic complexity class for a method.
 * Determined by static heuristics:
 *  - nested loop depth
 *  - recursion presence
 *  - loop count relative to nesting
 * ACADEMIC NOTE:
 * Exact Big-O computation from source code alone is undecidable in general.
 * These classifications are APPROXIMATIONS based on structural heuristics,
 * which is consistent with static analysis literature (e.g., McCabe, 1976).
 */

@Getter
public enum ComplexityClass {

    /** Constant time - no loops, no recursion */
    O_1("O(1)", 100, 1L, "Constant"),

    /** Linear - single loop or linear recursion */
    O_N("O(n)", 85, 10L, "Linear"),

    /** Quadratic - nested loop depth 2 or equivalent */
    O_N2("O(n²)", 50, 100L, "Quadratic"),

    /** Cubic - nested loop depth 3 */
    O_N3("O(n³)", 20, 1_000L, "Cubic"),

    /** Exponential - recursive branching, combinatorial patterns */
    O_2N("O(2ⁿ)", 5, 10_000L, "Exponential");

    /** Human-readable label */
    private final String label;

    /** Base EEI score for this complexity class (before penalties) */
    private final int baseEei;

    /** Relative energy cost multiplier (O(1) = 1x baseline) */
    private final long relativeCostMultiplier;

    /** Plain English description */
    private final String description;

    ComplexityClass(String label, int baseEei, long relativeCostMultiplier, String description) {
        this.label = label;
        this.baseEei = baseEei;
        this.relativeCostMultiplier = relativeCostMultiplier;
        this.description = description;
    }

    /** Format relative cost as human-readable string (e.g. "100x") */
    public String getRelativeCostLabel() {
        return relativeCostMultiplier + "x";
    }
}
