package com.energyanalyzer.controller;

import com.energyanalyzer.model.*;
import com.energyanalyzer.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DashboardController
 *
 * Serves the analysis results dashboard and handles export requests.
 *
 * Endpoints:
 *  GET /dashboard        → Main results dashboard
 *  GET /dashboard/api    → JSON API for chart data
 *  GET /export/csv       → CSV export of results
 *  GET /export/pdf       → PDF report export
 *  GET /demo             → Demo with sample data (no upload needed)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ExportService exportService;

    /** Main dashboard - requires analysis report in session */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        AnalysisReport report = (AnalysisReport) session.getAttribute("analysisReport");

        if (report == null) {
            // No report in session - redirect to demo or upload
            return "redirect:/demo";
        }

        enrichModel(model, report);
        return "dashboard";
    }

    /** Demo dashboard with pre-built sample data */
    @GetMapping("/demo")
    public String demo(Model model, HttpSession session) {
        AnalysisReport demoReport = buildDemoReport();
        session.setAttribute("analysisReport", demoReport);
        enrichModel(model, demoReport);
        model.addAttribute("isDemo", true);
        return "dashboard";
    }

    /** JSON API for chart.js data */
    @GetMapping(value = "/dashboard/api/chart-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getChartData(HttpSession session) {
        AnalysisReport report = (AnalysisReport) session.getAttribute("analysisReport");
        if (report == null) return Map.of("error", "No analysis data");

        List<MethodAnalysisResult> results = report.getMethodResults();

        // EEI per method
        List<String> methodLabels = results.stream()
                .map(MethodAnalysisResult::getFullMethodId).collect(Collectors.toList());
        List<Double> eeiScores = results.stream()
                .map(MethodAnalysisResult::getEeiScore).collect(Collectors.toList());
        List<Double> weightedEeiScores = results.stream()
                .map(MethodAnalysisResult::getWeightedEei).collect(Collectors.toList());

        // Tier distribution
        Map<String, Long> tierDist = report.getMethodResults().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getEnergyTier().getLabel(), Collectors.counting()));

        // Complexity distribution
        Map<String, Long> complexityDist = report.getMethodResults().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getComplexityClass().getLabel(), Collectors.counting()));

        // Centrality scores for call graph chart
        List<Double> centralityScores = results.stream()
                .map(MethodAnalysisResult::getCentralityScore).collect(Collectors.toList());

        Map<String, Object> chartData = new LinkedHashMap<>();
        chartData.put("methodLabels", methodLabels);
        chartData.put("eeiScores", eeiScores);
        chartData.put("weightedEeiScores", weightedEeiScores);
        chartData.put("tierDistribution", tierDist);
        chartData.put("complexityDistribution", complexityDist);
        chartData.put("centralityScores", centralityScores);
        chartData.put("hotspotCount", report.getTotalHotspots());
        chartData.put("totalMethods", report.getTotalMethodsAnalyzed());
        chartData.put("averageEei", String.format("%.1f", report.getAverageEei()));
        chartData.put("buildPassed", report.isBuildPassed());

        return chartData;
    }

    /** Export results as CSV */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(HttpSession session) {
        AnalysisReport report = (AnalysisReport) session.getAttribute("analysisReport");
        if (report == null) return ResponseEntity.notFound().build();

        try {
            byte[] csvBytes = exportService.exportToCsv(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"energy-analysis-" + report.getReportId() + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csvBytes);
        } catch (Exception e) {
            log.error("CSV export failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Export results as PDF report */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(HttpSession session) {
        AnalysisReport report = (AnalysisReport) session.getAttribute("analysisReport");
        if (report == null) return ResponseEntity.notFound().build();

        try {
            byte[] pdfBytes = exportService.exportToPdf(report);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"energy-analysis-" + report.getReportId() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("PDF export failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void enrichModel(Model model, AnalysisReport report) {
        model.addAttribute("report", report);
        model.addAttribute("methods", report.getMethodResults());
        model.addAttribute("hotspots", report.getHotspots());
        model.addAttribute("criticalMethods", report.getCriticalMethods());
        model.addAttribute("buildPassed", report.isBuildPassed());
        model.addAttribute("projectGrade", report.getProjectGrade());
        model.addAttribute("EnergyTier", EnergyTier.class);
        model.addAttribute("ComplexityClass", ComplexityClass.class);
    }

    // ── Demo Data Builder ─────────────────────────────────────────────────────

    /**
     * Build a realistic sample analysis report for demonstration purposes.
     * Covers a range of efficiency levels, complexity classes, and hotspot scenarios.
     */
    private AnalysisReport buildDemoReport() {
        List<MethodAnalysisResult> methods = new ArrayList<>();

        // Method 1: Good - Simple getter
        methods.add(buildMethod("UserService", "getUserById", ComplexityClass.O_1,
                95.0, 95.0, EnergyTier.LOW, 1, 12, 2, 0.8, false, "1x"));

        // Method 2: Good - Linear search
        methods.add(buildMethod("OrderService", "findOrdersByStatus", ComplexityClass.O_N,
                78.0, 85.0, EnergyTier.LOW, 1, 5, 3, 0.4, false, "10x"));

        // Method 3: Warning - Quadratic sort
        methods.add(buildMethod("ReportService", "sortTransactions", ComplexityClass.O_N2,
                45.0, 63.0, EnergyTier.MEDIUM, 2, 3, 1, 0.3, false, "100x"));

        // Method 4: Critical hotspot - Recursive + nested loops
        methods.add(buildMethod("AnalyticsEngine", "processAllRecords", ComplexityClass.O_N2,
                28.0, 84.0, EnergyTier.CRITICAL, 4, 15, 6, 0.95, true, "100x"));

        // Method 5: High impact - Central utility method with poor EEI
        methods.add(buildMethod("DataProcessor", "transformData", ComplexityClass.O_N,
                52.0, 130.0, EnergyTier.MEDIUM, 3, 22, 4, 0.99, true, "10x"));

        // Method 6: Critical - Exponential recursion
        methods.add(buildMethod("AlgorithmService", "computeFibonacci", ComplexityClass.O_2N,
                5.0, 7.5, EnergyTier.CRITICAL, 2, 1, 0, 0.05, true, "10000x"));

        // Method 7: Good - Constant time lookup
        methods.add(buildMethod("CacheService", "getFromCache", ComplexityClass.O_1,
                92.0, 184.0, EnergyTier.LOW, 0, 18, 1, 0.9, false, "1x"));

        // Method 8: High impact - Many I/O calls
        methods.add(buildMethod("DatabaseService", "bulkInsert", ComplexityClass.O_N,
                35.0, 42.0, EnergyTier.HIGH, 5, 3, 8, 0.2, true, "10x"));

        // Method 9: Medium - String concat issues
        methods.add(buildMethod("ReportBuilder", "buildHtmlReport", ComplexityClass.O_N,
                48.0, 52.0, EnergyTier.MEDIUM, 3, 2, 0, 0.1, false, "10x"));

        // Method 10: Good - Cubic but isolated
        methods.add(buildMethod("MatrixService", "multiply3D", ComplexityClass.O_N3,
                18.0, 18.0, EnergyTier.CRITICAL, 2, 0, 3, 0.0, true, "1000x"));

        double avgEei = methods.stream().mapToDouble(MethodAnalysisResult::getEeiScore).average().orElse(0);
        long hotspots = methods.stream().filter(MethodAnalysisResult::isHotspot).count();

        Map<EnergyTier, Long> tierDist = methods.stream()
                .collect(Collectors.groupingBy(MethodAnalysisResult::getEnergyTier, Collectors.counting()));
        Map<ComplexityClass, Long> complexDist = methods.stream()
                .collect(Collectors.groupingBy(MethodAnalysisResult::getComplexityClass, Collectors.counting()));

        return AnalysisReport.builder()
                .reportId("DEMO01")
                .projectName("Demo E-Commerce System")
                .analysisTimestamp(java.time.LocalDateTime.now())
                .analysisDurationMs(847)
                .methodResults(methods)
                .callGraph(new CallGraph())
                .totalMethodsAnalyzed(methods.size())
                .totalHotspots((int) hotspots)
                .averageEei(avgEei)
                .averageWeightedEei(methods.stream().mapToDouble(MethodAnalysisResult::getWeightedEei).average().orElse(0))
                .methodsBelowThreshold(3)
                .buildPassed(false)
                .buildFailureReasons(List.of(
                        "BUILD VIOLATION: Method AnalyticsEngine#processAllRecords has EEI 28.0, below minimum threshold 60.0",
                        "BUILD VIOLATION: Method AlgorithmService#computeFibonacci has EEI 5.0, below critical threshold 30.0",
                        "BUILD VIOLATION: 5 hotspot methods detected, exceeding maximum allowed (5)"))
                .sessionCpuUsagePercent(12.4)
                .sessionMemoryUsedMb(342L)
                .analyzerJvmVersion("17.0.9")
                .tierDistribution(tierDist)
                .complexityDistribution(complexDist)
                .build();
    }

    private MethodAnalysisResult buildMethod(String className, String methodName,
            ComplexityClass complexity, double eei, double weightedEei,
            EnergyTier tier, int antiPatternCount, int fanIn, int fanOut,
            double centrality, boolean hotspot, String relativeCost) {

        List<String> antiPatterns = new ArrayList<>();
        if (antiPatternCount > 0) antiPatterns.add("Nested loops detected");
        if (antiPatternCount > 1) antiPatterns.add("High cyclomatic complexity");
        if (antiPatternCount > 2) antiPatterns.add("String concatenation in loop");
        if (antiPatternCount > 3) antiPatterns.add("Excessive I/O operations");
        if (antiPatternCount > 4) antiPatterns.add("Excessive object creation");

        List<String> hotspotReasons = hotspot
                ? List.of("EEI below threshold", "High structural impact detected")
                : List.of();

        return MethodAnalysisResult.builder()
                .methodName(methodName)
                .className(className)
                .sourceFile(className.replace(".", "/") + ".java")
                .startLine((int)(Math.random() * 200 + 10))
                .complexityClass(complexity)
                .relativeCost(relativeCost)
                .eeiScore(eei)
                .weightedEei(weightedEei)
                .weightMultiplier(Math.max(1.0, weightedEei / eei))
                .energyTier(tier)
                .fanIn(fanIn)
                .fanOut(fanOut)
                .centralityScore(centrality)
                .callChainDepth(fanOut > 0 ? 2 : 0)
                .hotspot(hotspot)
                .hotspotReasons(new ArrayList<>(hotspotReasons))
                .antiPatterns(antiPatterns)
                .suggestions(new ArrayList<>())
                .build();
    }
}
