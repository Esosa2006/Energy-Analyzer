package com.energyanalyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Raw static metrics extracted from a single Java method via JavaParser.
 *
 * These are the foundational data points used by all downstream services
 * (complexity classification, EEI calculation, hotspot detection, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticMetrics {

    /** Fully qualified method name (ClassName#methodName) */
    private String methodName;

    /** Name of the class containing this method */
    private String className;

    /** Source file path relative to project root */
    private String sourceFile;

    /** Line number where the method starts */
    private int startLine;

    /**
     * Cyclomatic complexity = number of linearly independent paths.
     * Formula: CC = E - N + 2P (simplified: 1 + number of decision points)
     * Decision points include: if, else-if, for, while, do-while, case, catch, &&, ||
     */
    private int cyclomaticComplexity;

    /** Total number of loop constructs (for, while, do-while, enhanced-for) */
    private int loopCount;

    /**
     * Maximum nesting depth of loops.
     * Nested loops → O(n²) or worse complexity.
     * Depth 2 = O(n²), Depth 3 = O(n³), etc.
     */
    private int nestedLoopDepth;

    /** Lines of Code (including blank lines and comments) */
    private int linesOfCode;

    /** Whether this method calls itself directly or indirectly */
    private boolean recursive;

    /**
     * Count of String concatenation via '+' operator.
     * Using + in loops creates many intermediate String objects (O(n²) memory).
     * Should be replaced with StringBuilder.
     */
    private int stringConcatenationCount;

    /**
     * Count of 'new' keyword invocations.
     * Excessive object creation increases GC pressure → higher energy usage.
     */
    private int objectCreationCount;

    /**
     * Count of I/O operations (file read/write, network calls, DB queries).
     * I/O is extremely expensive in terms of energy and latency.
     */
    private int ioOperationCount;

    /** Total number of method calls made from within this method */
    private int methodCallCount;

    /**
     * Fan-in: number of OTHER methods in the codebase that call this method.
     * High fan-in → structurally central → inefficiencies amplified.
     */
    private int fanIn;

    /**
     * Fan-out: number of OTHER methods this method calls.
     * High fan-out → complex dependencies → harder to optimize.
     */
    private int fanOut;

    /**
     * Maximum depth of the call chain starting from this method.
     * Computed via BFS/DFS on the call graph.
     */
    private int callChainDepth;
}
