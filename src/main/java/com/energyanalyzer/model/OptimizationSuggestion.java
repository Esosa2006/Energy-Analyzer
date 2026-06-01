package com.energyanalyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * A single, actionable optimization suggestion for a method.
 *
 * Each suggestion includes:
 *  - The specific issue detected
 *  - Why it matters (energy/performance impact)
 *  - How to fix it (concrete guidance)
 *  - Priority level
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationSuggestion {

    public enum Priority { HIGH, MEDIUM, LOW }
    public enum Category {
        COMPLEXITY, LOOP_OPTIMIZATION, STRING_HANDLING,
        MEMORY, IO_OPTIMIZATION, CACHING, REFACTORING
    }

    /** Short title of the suggestion */
    private String title;

    /** The specific code issue detected */
    private String issue;

    /**
     * Why this matters from a performance/energy perspective.
     * Example: "String concatenation in loops creates O(n²) string copies,
     * significantly increasing heap allocation and GC pressure."
     */
    private String whyItMatters;

    /**
     * How to fix it.
     * Example: "Replace String + operator in loop with StringBuilder.append().
     * Use sb.toString() after the loop completes."
     */
    private String howToFix;

    /** Code example showing the fix (optional) */
    private String codeExample;

    /** Priority of this suggestion */
    private Priority priority;

    /** Category this suggestion belongs to */
    private Category category;

    /** Estimated EEI improvement if applied (approximate) */
    private int estimatedEeiGain;
}
