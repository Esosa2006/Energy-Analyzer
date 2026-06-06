package com.energyanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result of Stream API detection for a single method.
 * Carries:
 *   - whether streams were detected
 *   - estimated complexity contribution
 *   - confidence level (HIGH / MEDIUM / LOW)
 *   - human-readable chain description
 * This feeds into ComplexityClassifierService.adjustForStreamComplexity()
 * which only upgrades complexity — never downgrades existing loop-based
 * classifications.
 * IMPORTANT: This does not replace existing complexity analysis.
 * It supplements it.
 */
@Data
@AllArgsConstructor
public class StreamAnalysisResult {

    public enum StreamComplexity {
        NONE,
        O_N,                        // filter, map, collect, forEach, etc.
        O_N_LOG_N,                  // sorted()
        O_N2,                       // flatMap(), nested streams
        STREAM_BASED_OPERATION,     // detected but cannot determine exactly → estimate O(n)
        STREAM_BASED_OPERATION_N2   // detected with nesting → estimate O(n²)
    }

    /** Whether any stream operations were detected */
    private boolean streamDetected;

    /** Estimated complexity of the stream operation */
    private StreamComplexity complexity;

    /**
     * Confidence in the complexity estimate:
     *   HIGH   — clear chain detected (e.g. stream().sorted().collect())
     *   MEDIUM — conservative estimate applied (e.g. flatMap detected)
     *   LOW    — stream usage detected but chain unclear
     */
    private String confidence;

    /** Human-readable description of detected chain e.g. "stream() → filter() → collect()" */
    private String detectedChain;

    /** Factory: no stream usage detected */
    public static StreamAnalysisResult none() {
        return new StreamAnalysisResult(false, StreamComplexity.NONE, "HIGH", "");
    }

    /** Whether this result carries meaningful complexity information */
    public boolean hasComplexityImpact() {
        return streamDetected && complexity != StreamComplexity.NONE;
    }

    /** Label shown on dashboard */
    public String getComplexityLabel() {
        return switch (complexity) {
            case O_N              -> "O(n) via Stream";
            case O_N_LOG_N        -> "O(n log n) via sorted()";
            case O_N2             -> "O(n²) via flatMap/nested";
            case STREAM_BASED_OPERATION    -> "Stream O(n) est.";
            case STREAM_BASED_OPERATION_N2 -> "Stream O(n²) est.";
            default               -> "";
        };
    }
}