package com.energyanalyzer.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Top-level analysis report for an entire Java project or file.
 *
 * Contains:
 *  - All per-method results
 *  - The call graph
 *  - Project-wide summary statistics
 *  - Build gate result (pass/fail)
 *  - Optional system metrics from the analysis session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReport {

    // ── Report Identity ───────────────────────────────────────────────────────

    private String reportId;
    private String projectName;
    private LocalDateTime analysisTimestamp;
    private long analysisDurationMs;

    // ── Results ───────────────────────────────────────────────────────────────

    @Builder.Default
    private List<MethodAnalysisResult> methodResults = new ArrayList<>();

    private CallGraph callGraph;

    // ── Summary Statistics ────────────────────────────────────────────────────

    private int totalMethodsAnalyzed;
    private int totalHotspots;
    private double averageEei;
    private double averageWeightedEei;
    private int methodsBelowThreshold;

    // ── Build Gate ────────────────────────────────────────────────────────────

    /** Whether the project passed all configured quality thresholds */
    private boolean buildPassed;

    /** Reason for build failure, if applicable */
    @Builder.Default
    private List<String> buildFailureReasons = new ArrayList<>();

    // ── System Metrics (Analysis Session Only) ────────────────────────────────

    /**
     * IMPORTANT: These metrics reflect the ANALYZER'S resource usage
     * during the analysis session. They do NOT represent the energy
     * consumption of the analyzed Java code.
     */
    private Double sessionCpuUsagePercent;
    private Long sessionMemoryUsedMb;
    private String analyzerJvmVersion;

    // ── Tier Distribution ─────────────────────────────────────────────────────

    private Map<EnergyTier, Long> tierDistribution;
    private Map<ComplexityClass, Long> complexityDistribution;

    // ── Computed Helpers ──────────────────────────────────────────────────────

    public List<MethodAnalysisResult> getHotspots() {
        return methodResults.stream()
                .filter(MethodAnalysisResult::isHotspot)
                .collect(Collectors.toList());
    }

    public List<MethodAnalysisResult> getCriticalMethods() {
        return methodResults.stream()
                .filter(r -> r.getEnergyTier() == EnergyTier.CRITICAL)
                .collect(Collectors.toList());
    }

    public String getFormattedTimestamp() {
        if (analysisTimestamp == null) return "Unknown";
        return analysisTimestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public String getFormattedDuration() {
        if (analysisDurationMs < 1000) return analysisDurationMs + "ms";
        return String.format("%.2fs", analysisDurationMs / 1000.0);
    }

    /** Overall project health grade based on average EEI */
    public String getProjectGrade() {
        if (averageEei >= 80) return "A";
        if (averageEei >= 70) return "B";
        if (averageEei >= 60) return "C";
        if (averageEei >= 50) return "D";
        return "F";
    }
}
