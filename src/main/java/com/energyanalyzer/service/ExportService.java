package com.energyanalyzer.service;

import com.energyanalyzer.model.*;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;

/**
 * ExportService
 *
 * Handles export of analysis reports to:
 *  - CSV: for spreadsheet import and further analysis
 *  - PDF: for documentation, academic submission, stakeholder presentation
 */
@Slf4j
@Service
public class ExportService {

    /**
     * Export analysis report to CSV format.
     *
     * Columns: Method, Class, Complexity, EEI, WeightedEEI, Tier,
     *          RelativeCost, FanIn, FanOut, Centrality, Hotspot, AntiPatterns
     */
    public byte[] exportToCsv(AnalysisReport report) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos))) {
            // Header row
            writer.writeNext(new String[]{
                "Method Name", "Class", "Source File", "Start Line",
                "Complexity Class", "EEI Score", "Weighted EEI", "Weight Multiplier",
                "Energy Tier", "Relative Cost", "Fan-In", "Fan-Out",
                "Centrality Score", "Call Chain Depth",
                "Hotspot", "Anti-Pattern Count", "Anti-Patterns", "Suggestion Count"
            });

            // Data rows
            for (MethodAnalysisResult r : report.getMethodResults()) {
                writer.writeNext(new String[]{
                    r.getMethodName(),
                    r.getClassName(),
                    r.getSourceFile(),
                    String.valueOf(r.getStartLine()),
                    r.getComplexityClass().getLabel(),
                    String.format("%.1f", r.getEeiScore()),
                    String.format("%.1f", r.getWeightedEei()),
                    String.format("%.2f", r.getWeightMultiplier()),
                    r.getEnergyTier().getLabel(),
                    r.getRelativeCost(),
                    String.valueOf(r.getFanIn()),
                    String.valueOf(r.getFanOut()),
                    String.format("%.3f", r.getCentralityScore()),
                    String.valueOf(r.getCallChainDepth()),
                    r.isHotspot() ? "YES" : "NO",
                    String.valueOf(r.getAntiPatterns().size()),
                    String.join("; ", r.getAntiPatterns()),
                    String.valueOf(r.getSuggestions().size())
                });
            }

            // Summary row
            writer.writeNext(new String[0]);
            writer.writeNext(new String[]{"=== SUMMARY ==="});
            writer.writeNext(new String[]{"Report ID", report.getReportId()});
            writer.writeNext(new String[]{"Project", report.getProjectName()});
            writer.writeNext(new String[]{"Total Methods", String.valueOf(report.getTotalMethodsAnalyzed())});
            writer.writeNext(new String[]{"Average EEI", String.format("%.1f", report.getAverageEei())});
            writer.writeNext(new String[]{"Total Hotspots", String.valueOf(report.getTotalHotspots())});
            writer.writeNext(new String[]{"Build Passed", report.isBuildPassed() ? "YES" : "NO"});
            writer.writeNext(new String[]{"Project Grade", report.getProjectGrade()});
        }

        return baos.toByteArray();
    }

    /**
     * Export analysis report to PDF format.
     *
     * Generates a professional PDF report suitable for:
     *  - Academic project submission
     *  - Stakeholder presentation
     *  - CI/CD artifact archival
     */
    public byte[] exportToPdf(AnalysisReport report) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        try {
            // ── Title Page ────────────────────────────────────────────────
            Paragraph title = new Paragraph("Java Code Efficiency & Energy-Aware Performance Analyzer")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.DARK_GRAY);
            document.add(title);

            document.add(new Paragraph("Analysis Report")
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

            document.add(new Paragraph("\n"));

            // ── Summary Section ───────────────────────────────────────────
            document.add(new Paragraph("Report Summary").setFontSize(14).setBold());

            Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{40, 60}));
            summaryTable.setWidth(UnitValue.createPercentValue(100));
            addTableRow(summaryTable, "Report ID", report.getReportId());
            addTableRow(summaryTable, "Project Name", report.getProjectName());
            addTableRow(summaryTable, "Analysis Timestamp", report.getFormattedTimestamp());
            addTableRow(summaryTable, "Total Methods Analyzed", String.valueOf(report.getTotalMethodsAnalyzed()));
            addTableRow(summaryTable, "Average EEI Score", String.format("%.1f / 100", report.getAverageEei()));
            addTableRow(summaryTable, "Total Hotspots", String.valueOf(report.getTotalHotspots()));
            addTableRow(summaryTable, "Methods Below Threshold", String.valueOf(report.getMethodsBelowThreshold()));
            addTableRow(summaryTable, "Project Grade", report.getProjectGrade());
            addTableRow(summaryTable, "Build Status", report.isBuildPassed() ? "✓ PASSED" : "✗ FAILED");
            addTableRow(summaryTable, "Analysis Duration", report.getFormattedDuration());
            document.add(summaryTable);

            document.add(new Paragraph("\n"));

            // ── IMPORTANT Disclaimer ──────────────────────────────────────
            Paragraph disclaimer = new Paragraph(
                "IMPORTANT: The Energy Efficiency Index (EEI) is a relative software efficiency indicator " +
                "derived from static analysis. It does NOT represent actual energy consumption in Joules. " +
                "EEI correlates with algorithmic complexity, structural importance, and known coding anti-patterns, " +
                "providing an academically grounded proxy for relative energy impact.")
                    .setFontSize(9)
                    .setItalic()
                    .setFontColor(ColorConstants.DARK_GRAY)
                    .setBorder(new SolidBorder(1));
            document.add(disclaimer);

            document.add(new Paragraph("\n"));

            // ── Build Failures ────────────────────────────────────────────
            if (!report.isBuildPassed() && !report.getBuildFailureReasons().isEmpty()) {
                document.add(new Paragraph("Build Quality Gate Violations").setFontSize(12).setBold());
                for (String violation : report.getBuildFailureReasons()) {
                    document.add(new Paragraph("• " + violation).setFontSize(9)
                            .setFontColor(ColorConstants.RED));
                }
                document.add(new Paragraph("\n"));
            }

            // ── Methods Table ─────────────────────────────────────────────
            document.add(new Paragraph("Method Analysis Results").setFontSize(14).setBold());

            Table methodTable = new Table(UnitValue.createPercentArray(
                    new float[]{20, 12, 8, 10, 10, 8, 8, 8, 8, 8}));
            methodTable.setWidth(UnitValue.createPercentValue(100));
            methodTable.setFontSize(8);

            // Header
            String[] headers = {"Method", "Class", "Complexity", "EEI", "W-EEI",
                                 "Tier", "Cost", "Fan-In", "Fan-Out", "Hotspot"};
            for (String h : headers) {
                Cell cell = new Cell().add(new Paragraph(h).setBold())
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY);
                methodTable.addHeaderCell(cell);
            }

            // Data rows
            for (MethodAnalysisResult r : report.getMethodResults()) {
                methodTable.addCell(r.getMethodName());
                methodTable.addCell(r.getClassName());
                methodTable.addCell(r.getComplexityClass().getLabel());
                methodTable.addCell(r.getEeiFormatted());
                methodTable.addCell(r.getWeightedEeiFormatted());
                methodTable.addCell(r.getEnergyTier().getLabel().split(" ")[0]);
                methodTable.addCell(r.getRelativeCost());
                methodTable.addCell(String.valueOf(r.getFanIn()));
                methodTable.addCell(String.valueOf(r.getFanOut()));
                methodTable.addCell(r.isHotspot() ? "⚠ YES" : "✓ No");
            }

            document.add(methodTable);

            // ── Hotspot Details ───────────────────────────────────────────
            List<MethodAnalysisResult> hotspots = report.getHotspots();
            if (!hotspots.isEmpty()) {
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("Hotspot Details").setFontSize(14).setBold());

                for (MethodAnalysisResult hs : hotspots) {
                    document.add(new Paragraph(hs.getFullMethodId())
                            .setFontSize(11).setBold().setFontColor(ColorConstants.RED));

                    document.add(new Paragraph(String.format(
                            "EEI: %.1f | Tier: %s | Complexity: %s | Fan-In: %d",
                            hs.getEeiScore(), hs.getEnergyTier().getLabel(),
                            hs.getComplexityClass().getLabel(), hs.getFanIn()))
                            .setFontSize(9));

                    for (String reason : hs.getHotspotReasons()) {
                        document.add(new Paragraph("  • " + reason).setFontSize(9));
                    }

                    if (!hs.getSuggestions().isEmpty()) {
                        document.add(new Paragraph("  Recommendations:").setFontSize(9).setBold());
                        for (OptimizationSuggestion s : hs.getSuggestions()) {
                            document.add(new Paragraph("    → " + s.getTitle() + ": " + s.getHowToFix())
                                    .setFontSize(8));
                        }
                    }
                    document.add(new Paragraph("\n"));
                }
            }

            // ── Session Metrics Footer ────────────────────────────────────
            if (report.getSessionCpuUsagePercent() != null) {
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("Analysis Session Metrics (Analyzer Resource Usage)")
                        .setFontSize(10).setBold().setItalic());
                document.add(new Paragraph(
                        "NOTE: These metrics reflect the ANALYZER's own resource usage, " +
                        "NOT the energy consumption of the analyzed code.")
                        .setFontSize(8).setItalic().setFontColor(ColorConstants.GRAY));
                document.add(new Paragraph(String.format(
                        "CPU Usage: %.1f%% | Memory Used: %d MB | JVM: %s | Duration: %s",
                        report.getSessionCpuUsagePercent(),
                        report.getSessionMemoryUsedMb(),
                        report.getAnalyzerJvmVersion(),
                        report.getFormattedDuration()))
                        .setFontSize(9));
            }

        } finally {
            document.close();
        }

        return baos.toByteArray();
    }

    private void addTableRow(Table table, String key, String value) {
        table.addCell(new Cell().add(new Paragraph(key).setBold()).setFontSize(9));
        table.addCell(new Cell().add(new Paragraph(value)).setFontSize(9));
    }
}
