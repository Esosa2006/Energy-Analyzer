package com.energyanalyzer.cli;

import com.energyanalyzer.model.*;
import com.energyanalyzer.service.StaticAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * CliRunner
 *
 * Runs the energy analyzer from the command line.
 * Designed for CI/CD pipeline integration.
 *
 * Usage:
 *   java -jar analyzer.jar /path/to/java/project
 *   java -jar analyzer.jar /path/to/java/project --threshold=70
 *   java -jar analyzer.jar /path/to/java/project --warn-only
 *
 * Exit codes:
 *   0 = Analysis passed all thresholds (build success)
 *   1 = Analysis failed thresholds (build failure)
 *   2 = Analysis error (invalid path, parse failure, etc.)
 *
 * This is used by GitHub Actions, Jenkins, and Maven/Gradle plugins
 * to enforce energy efficiency quality gates in the build pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CliRunner {

    private final StaticAnalyzerService analyzerService;

    private static final String SEPARATOR = "═".repeat(70);

    /**
     * Run CLI analysis and return exit code.
     *
     * @param args Command line arguments [path, options...]
     * @return Exit code (0=pass, 1=fail, 2=error)
     */
    public int run(String[] args) {
        printBanner();

        if (args.length == 0) {
            printUsage();
            return 2;
        }

        String projectPath = args[0];
        String projectName = extractOption(args, "--name=", Path.of(projectPath).getFileName().toString());

        System.out.println("  Project Path: " + projectPath);
        System.out.println("  Project Name: " + projectName);
        System.out.println(SEPARATOR);

        try {
            Path path = Paths.get(projectPath);

            if (!path.toFile().exists()) {
                System.err.println("ERROR: Path does not exist: " + projectPath);
                return 2;
            }

            System.out.println("\n  Running static analysis...\n");

            AnalysisReport report = analyzerService.analyze(path, projectName);

            printReport(report);

            if (report.isBuildPassed()) {
                System.out.println("\n" + SEPARATOR);
                System.out.println("  ✓  BUILD PASSED - All quality thresholds met");
                System.out.println("     Average EEI: " + String.format("%.1f", report.getAverageEei()));
                System.out.println("     Project Grade: " + report.getProjectGrade());
                System.out.println(SEPARATOR + "\n");
                return 0;
            } else {
                System.out.println("\n" + SEPARATOR);
                System.out.println("  ✗  BUILD FAILED - Quality gate violations detected");
                System.out.println(SEPARATOR);
                for (String reason : report.getBuildFailureReasons()) {
                    System.out.println("     " + reason);
                }
                System.out.println(SEPARATOR + "\n");
                return 1;
            }

        } catch (Exception e) {
            System.err.println("\nANALYSIS ERROR: " + e.getMessage());
            log.error("CLI analysis failed", e);
            return 2;
        }
    }

    // ── Report Printing ────────────────────────────────────────────────────────

    private void printReport(AnalysisReport report) {
        System.out.println("  ANALYSIS SUMMARY");
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.printf("  %-30s %s%n", "Report ID:", report.getReportId());
        System.out.printf("  %-30s %s%n", "Methods Analyzed:", report.getTotalMethodsAnalyzed());
        System.out.printf("  %-30s %.1f / 100%n", "Average EEI:", report.getAverageEei());
        System.out.printf("  %-30s %s%n", "Project Grade:", report.getProjectGrade());
        System.out.printf("  %-30s %d%n", "Total Hotspots:", report.getTotalHotspots());
        System.out.printf("  %-30s %d%n", "Methods Below Threshold:", report.getMethodsBelowThreshold());
        System.out.printf("  %-30s %s%n", "Analysis Duration:", report.getFormattedDuration());
        System.out.println();

        // Method results table
        System.out.println("  METHOD ANALYSIS RESULTS");
        System.out.println("  ─────────────────────────────────────────────────────────────────────────");
        System.out.printf("  %-35s %-8s %-7s %-12s %-6s %-8s%n",
                "Method", "Cmplx", "EEI", "Tier", "Hot?", "Relative$");
        System.out.println("  ─────────────────────────────────────────────────────────────────────────");

        List<MethodAnalysisResult> results = report.getMethodResults();
        results.sort((a, b) -> Double.compare(a.getEeiScore(), b.getEeiScore())); // worst first

        for (MethodAnalysisResult r : results) {
            String hotspot = r.isHotspot() ? "⚠ YES" : "  No ";
            String tier = r.getEnergyTier().name();
            System.out.printf("  %-35s %-8s %-7s %-12s %-6s %-8s%n",
                    truncate(r.getFullMethodId(), 35),
                    r.getComplexityClass().getLabel(),
                    r.getEeiFormatted(),
                    tier,
                    hotspot,
                    r.getRelativeCost());
        }

        System.out.println();

        // Hotspot details
        if (!report.getHotspots().isEmpty()) {
            System.out.println("  ⚠  HOTSPOT DETAILS");
            System.out.println("  ─────────────────────────────────────────────────────────────");
            for (MethodAnalysisResult hs : report.getHotspots()) {
                System.out.println("  • " + hs.getFullMethodId()
                        + " [EEI=" + hs.getEeiFormatted()
                        + " | " + hs.getComplexityClass().getLabel()
                        + " | Fan-in=" + hs.getFanIn() + "]");
                for (String reason : hs.getHotspotReasons()) {
                    System.out.println("      → " + reason);
                }
                if (!hs.getSuggestions().isEmpty()) {
                    System.out.println("    Suggestions:");
                    for (OptimizationSuggestion s : hs.getSuggestions()) {
                        System.out.println("      [" + s.getPriority() + "] " + s.getTitle());
                    }
                }
                System.out.println();
            }
        }

        // Session metrics
        if (report.getSessionCpuUsagePercent() != null) {
            System.out.println("  ANALYSIS SESSION METRICS (Analyzer Resource Usage - NOT code energy)");
            System.out.println("  ─────────────────────────────────────────────────────────────");
            System.out.printf("  CPU: %.1f%% | Memory: %dMB | JVM: %s%n",
                    report.getSessionCpuUsagePercent(),
                    report.getSessionMemoryUsedMb(),
                    report.getAnalyzerJvmVersion());
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println(SEPARATOR);
        System.out.println("  Java Code Efficiency & Energy-Aware Performance Analyzer");
        System.out.println("  Static Analysis Tool for Performance and Efficiency Evaluation");
        System.out.println("  ─────────────────────────────────────────────────────────────────────");
        System.out.println("  NOTE: EEI is a relative software efficiency indicator (NOT Joules).");
        System.out.println("        Based on complexity, structural importance, and anti-patterns.");
        System.out.println(SEPARATOR);
        System.out.println();
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar analyzer.jar <path-to-java-project>");
        System.out.println("  java -jar analyzer.jar <path> --name=\"My Project\"");
        System.out.println("  java -jar analyzer.jar <path> --warn-only (don't fail build)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar analyzer.jar ./src/main/java/");
        System.out.println("  java -jar analyzer.jar ./MyService.java");
        System.out.println("  java -jar analyzer.jar /project/src --name=\"E-Commerce Backend\"");
    }

    private String extractOption(String[] args, String prefix, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length()).trim();
            }
        }
        return defaultValue;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : "..." + s.substring(s.length() - (maxLen - 3));
    }
}
