package com.energyanalyzer.controller;

import com.energyanalyzer.model.AnalysisReport;
import com.energyanalyzer.service.StaticAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

/**
 * UploadController
 *
 * Handles Java file/project uploads for web-based analysis.
 *
 * Supported upload types:
 *  - Single .java file
 *  - Multiple .java files (from a project)
 *  - ZIP file containing a Java project (TODO: future extension)
 *
 * After upload:
 *  1. Files are saved to a temp directory
 *  2. StaticAnalyzerService runs the analysis pipeline
 *  3. Results are stored in session and user is redirected to dashboard
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class UploadController {

    private final StaticAnalyzerService analyzerService;

    @Value("${energy-analyzer.upload.temp-dir:/tmp/energy-analyzer-uploads}")
    private String uploadTempDir;

    /** Show the main upload page */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Java Energy Analyzer");
        return "index";
    }

    /**
     * Handle Java file upload(s) and trigger analysis.
     *
     * Accepts one or more .java files, saves them to temp storage,
     * runs the static analysis pipeline, and redirects to the dashboard.
     */
    @PostMapping("/upload")
    public String uploadFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "projectName", defaultValue = "Uploaded Project") String projectName,
            RedirectAttributes redirectAttrs,
            jakarta.servlet.http.HttpSession session) {

        if (files == null || files.isEmpty() || files.get(0).isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "Please select at least one Java file to analyze.");
            return "redirect:/";
        }

        // Create a unique temp directory for this upload session
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        Path uploadDir = Path.of(uploadTempDir, sessionId);

        try {
            Files.createDirectories(uploadDir);

            // Save uploaded files
            int javaFilesCount = 0;
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String filename = sanitizeFilename(file.getOriginalFilename());
                if (!filename.endsWith(".java")) {
                    log.warn("Skipping non-Java file: {}", filename);
                    continue;
                }
                Path destination = uploadDir.resolve(filename);
                file.transferTo(destination);
                javaFilesCount++;
                log.info("Saved uploaded file: {}", destination);
            }

            if (javaFilesCount == 0) {
                redirectAttrs.addFlashAttribute("error",
                        "No valid .java files found in upload. Please upload Java source files.");
                return "redirect:/";
            }

            log.info("Analyzing {} Java file(s) for project '{}'", javaFilesCount, projectName);

            // Run the analysis pipeline
            AnalysisReport report = analyzerService.analyze(uploadDir, projectName);

            // Store in session for dashboard display
            session.setAttribute("analysisReport", report);
            session.setAttribute("projectName", projectName);

            log.info("Analysis complete: report {} - {} methods analyzed",
                    report.getReportId(), report.getTotalMethodsAnalyzed());

            return "redirect:/dashboard";

        } catch (IOException e) {
            log.error("File upload/analysis failed", e);
            redirectAttrs.addFlashAttribute("error",
                    "Analysis failed: " + e.getMessage() + ". Please check your files and try again.");
            return "redirect:/";
        } finally {
            // Clean up temp files asynchronously (simplified: delete after analysis)
            // In production, use a scheduled cleanup task
            cleanupAsync(uploadDir);
        }
    }

    /** Sanitize filename to prevent path traversal attacks */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "Unknown.java";
        // Remove path components, keep only the filename
        return Path.of(filename).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void cleanupAsync(Path dir) {
        // In production, replace with @Async + @Scheduled cleanup
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait for response to complete
                Files.walk(dir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); }
                            catch (IOException ignored) {}
                        });
            } catch (Exception ignored) {}
        }).start();
    }
}
