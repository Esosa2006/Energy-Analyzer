package com.energyanalyzer.service;

import com.energyanalyzer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * StaticAnalyzerService
 * ORCHESTRATES the full analysis pipeline:
 * 1. Parse Java files → StaticMetrics (CodeParserService)
 * 2. Build call graph → CallGraph (CallGraphService)
 * 3. Enrich metrics with graph data (fan-in, fan-out, depth)
 * 4. Classify complexity → ComplexityClass (ComplexityClassifierService)
 * 5. Calculate EEI score (EEICalculatorService)
 * 6. Classify energy tier (EnergyTier)
 * 7. Calculate weighted EEI (WeightedEEIService)
 * 8. Detect hotspots (HotspotDetectorService)
 * 9. Generate suggestions (SuggestionEngineService)
 * 10. Validate thresholds → build gate (ThresholdValidationService)
 * 11. Assemble AnalysisReport
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaticAnalyzerService {

    private final CodeParserService codeParserService;
    private final ComplexityClassifierService complexityClassifier;
    private final EEICalculatorService eeiCalculator;
    private final CallGraphService callGraphService;
    private final WeightedEEIService weightedEeiService;
    private final HotspotDetectorService hotspotDetector;
    private final SuggestionEngineService suggestionEngine;
    private final ThresholdValidationService thresholdValidator;

    /**
     * Run the complete analysis pipeline on a Java project path.
     *
     * @param projectPath Path to a .java file or directory of Java sources
     * @param projectName Human-readable project name
     * @return Complete AnalysisReport
     */
    public AnalysisReport analyze(Path projectPath, String projectName) throws IOException {
        long startTime = System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();
        String reportId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("═══════════════════════════════════════════════════════════");
        log.info("  Java Energy Analyzer - Starting Analysis");
        log.info("  Report ID: {}", reportId);
        log.info("  Project: {}", projectName);
        log.info("  Path: {}", projectPath);
        log.info("═══════════════════════════════════════════════════════════");

        // ── Step 1: Parse all Java files ────────────────────────────────────
        log.info("[1/8] Parsing Java source files...");
        List<StaticMetrics> allMetrics = codeParserService.parseProject(projectPath);

        if (allMetrics.isEmpty()) {
            log.warn("No Java methods found to analyze at path: {}", projectPath);
            return buildEmptyReport(reportId, projectName, timestamp);
        }

        // ── Step 2: Build call graph ─────────────────────────────────────────
        log.info("[2/8] Building static call graph...");
        List<Path> sourceFiles = collectJavaFiles(projectPath);
        CallGraph callGraph = callGraphService.buildCallGraph(allMetrics, sourceFiles);
        callGraphService.buildEdgesFromSource(sourceFiles, callGraph);
        callGraphService.enrichMetricsWithGraphData(allMetrics, callGraph);

        log.info("  Call graph: {} nodes, {} edges",
                callGraph.size(), callGraph.edgeCount());

        // ── Steps 3-9: Per-method analysis ──────────────────────────────────
        log.info("[3/8] Running per-method analysis pipeline...");
        List<MethodAnalysisResult> results = new ArrayList<>();

        for (StaticMetrics metrics : allMetrics) {
            MethodAnalysisResult result = analyzeMethod(metrics, callGraph);
            results.add(result);
        }

        // ── Step 10: Threshold validation ────────────────────────────────────
        log.info("[4/8] Validating quality thresholds...");
        List<String> violations = thresholdValidator.validate(results);
        boolean buildPassed = !thresholdValidator.shouldFailBuild(violations);

        // ── Step 11: Collect session metrics ─────────────────────────────────
        log.info("[5/8] Collecting analysis session metrics...");
        Double cpuUsage = null;
        Long memoryUsedMb = null;
        try {
            SystemInfo si = new SystemInfo();
            CentralProcessor cpu = si.getHardware().getProcessor();
            long[] prevTicks = cpu.getSystemCpuLoadTicks();
            Thread.sleep(100);
            cpuUsage = cpu.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
            memoryUsedMb = (si.getHardware().getMemory().getTotal()
                          - si.getHardware().getMemory().getAvailable()) / (1024 * 1024);
        } catch (Exception e) {
            log.debug("OSHI metrics unavailable: {}", e.getMessage());
        }

        // ── Step 12: Compute summary statistics ──────────────────────────────
        log.info("[6/8] Computing summary statistics...");
        double avgEei = results.stream()
                .mapToDouble(MethodAnalysisResult::getEeiScore).average().orElse(0);
        double avgWeightedEei = results.stream()
                .mapToDouble(MethodAnalysisResult::getWeightedEei).average().orElse(0);
        long hotspotCount = results.stream().filter(MethodAnalysisResult::isHotspot).count();
        long belowThreshold = violations.stream()
                .filter(v -> v.contains("BUILD VIOLATION") && v.contains("below minimum")).count();

        Map<EnergyTier, Long> tierDistribution = results.stream()
                .collect(Collectors.groupingBy(MethodAnalysisResult::getEnergyTier, Collectors.counting()));
        Map<ComplexityClass, Long> complexityDistribution = results.stream()
                .collect(Collectors.groupingBy(MethodAnalysisResult::getComplexityClass, Collectors.counting()));

        long duration = System.currentTimeMillis() - startTime;

        log.info("═══════════════════════════════════════════════════════════");
        log.info("  Analysis Complete: {}ms | {} methods | Avg EEI: {}",
                duration, results.size(), String.format("%.1f", avgEei));
        log.info("  Build: {} | Hotspots: {} | Violations: {}",
                buildPassed ? "PASSED ✓" : "FAILED ✗", hotspotCount, violations.size());
        log.info("═══════════════════════════════════════════════════════════");

        // ── Step 13: Assemble the final report ───────────────────────────────
        return AnalysisReport.builder()
                .reportId(reportId)
                .projectName(projectName)
                .analysisTimestamp(timestamp)
                .analysisDurationMs(duration)
                .methodResults(results)
                .callGraph(callGraph)
                .totalMethodsAnalyzed(results.size())
                .totalHotspots((int) hotspotCount)
                .averageEei(avgEei)
                .averageWeightedEei(avgWeightedEei)
                .methodsBelowThreshold((int) belowThreshold)
                .buildPassed(buildPassed)
                .buildFailureReasons(violations)
                .sessionCpuUsagePercent(cpuUsage)
                .sessionMemoryUsedMb(memoryUsedMb)
                .analyzerJvmVersion(System.getProperty("java.version"))
                .tierDistribution(tierDistribution)
                .complexityDistribution(complexityDistribution)
                .build();
    }

    // ── Per-Method Analysis Pipeline ──────────────────────────────────────────

    private MethodAnalysisResult analyzeMethod(StaticMetrics metrics, CallGraph callGraph) {
        // Step A: Classify complexity
        ComplexityClass complexity = complexityClassifier.classify(metrics);

        // Adjust complexity upward if Stream API analysis detected
        // a heavier operation than loop analysis alone found
        complexity = complexityClassifier.adjustForStreamComplexity(
                complexity, metrics.getStreamAnalysis()
        );

        // Step B: Calculate EEI
        double eei = eeiCalculator.calculate(metrics, complexity);

        // Step C: Detect anti-patterns
        List<String> antiPatterns = eeiCalculator.detectAntiPatterns(metrics, complexity);

        // Step D: Classify energy tier
        EnergyTier tier = EnergyTier.fromEei(eei);

        // Step E: Get call graph node data
        String nodeId = CallGraphService.buildNodeId(metrics.getClassName(), metrics.getMethodName());
        MethodNode graphNode = callGraph.getNodes().get(nodeId);
        double weightMultiplier = graphNode != null ? graphNode.getWeightMultiplier() : 1.0;
        double centralityScore = graphNode != null ? graphNode.getCentralityScore() : 0.0;

        // Step F: Calculate weighted EEI
        double weightedEei = weightedEeiService.computeWeightedEei(eei, weightMultiplier);

        // Step G: Generate suggestions
        List<OptimizationSuggestion> suggestions = suggestionEngine.generateSuggestions(
                metrics, complexity, eei, metrics.getFanIn());

        // Build preliminary result for hotspot detection
        MethodAnalysisResult result = MethodAnalysisResult.builder()
                .methodName(metrics.getMethodName())
                .className(metrics.getClassName())
                .sourceFile(metrics.getSourceFile())
                .startLine(metrics.getStartLine())
                .metrics(metrics)
                .complexityClass(complexity)
                .relativeCost(complexity.getRelativeCostLabel())
                .eeiScore(eei)
                .weightedEei(weightedEei)
                .weightMultiplier(weightMultiplier)
                .energyTier(tier)
                .fanIn(metrics.getFanIn())
                .fanOut(metrics.getFanOut())
                .centralityScore(centralityScore)
                .callChainDepth(metrics.getCallChainDepth())
                .antiPatterns(antiPatterns)
                .suggestions(suggestions)
                .hotspot(false) // set below
                .build();

        // Step H: Detect hotspot (needs full result context)
        List<String> hotspotReasons = hotspotDetector.collectHotspotReasons(result);
        result.setHotspot(!hotspotReasons.isEmpty());
        result.setHotspotReasons(hotspotReasons);

        return result;
    }

    private List<Path> collectJavaFiles(Path root) throws IOException {
        if (Files.isRegularFile(root)) return List.of(root);
        List<Path> files = new ArrayList<>();
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(files::add);
        return files;
    }

    private AnalysisReport buildEmptyReport(String id, String name, LocalDateTime ts) {
        return AnalysisReport.builder()
                .reportId(id)
                .projectName(name)
                .analysisTimestamp(ts)
                .analysisDurationMs(0)
                .totalMethodsAnalyzed(0)
                .buildPassed(true)
                .buildFailureReasons(List.of("No Java methods found to analyze"))
                .build();
    }
}
