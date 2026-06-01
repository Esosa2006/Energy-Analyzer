package com.energyanalyzer;

import com.energyanalyzer.cli.CliRunner;
import com.energyanalyzer.model.ThresholdConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

@EnableConfigurationProperties(ThresholdConfiguration.class)

/**
 * Java Code Efficiency and Energy-Aware Performance Analyzer
 *
 * Entry point for both:
 *  1. Web mode  → spring-boot server (default)
 *  2. CLI mode  → java -jar analyzer.jar <path-to-java-project>
 *
 * IMPORTANT ACADEMIC NOTE:
 * This system does NOT measure actual energy consumption in Joules.
 * It computes a relative Energy Efficiency Index (EEI) derived from:
 *   - Algorithmic complexity class
 *   - Static code anti-patterns
 *   - Structural centrality in the call graph
 * All outputs are software efficiency indicators, not physical energy measurements.
 */
@SpringBootApplication
public class EnergyAnalyzerApplication {

    public static void main(String[] args) {
        // If a path argument is provided → run in CLI mode (no web server)
        if (args.length > 0 && !args[0].startsWith("--")) {
            runCliMode(args);
        } else {
            // Default: start the web application
            SpringApplication.run(EnergyAnalyzerApplication.class, args);
        }
    }

    /**
     * CLI mode: analyze a Java project from the command line.
     * Useful for CI/CD pipeline integration.
     *
     * Usage: java -jar analyzer.jar /path/to/java/project [--threshold=60]
     */
    private static void runCliMode(String[] args) {
        // Start Spring context without starting the web server
        System.setProperty("spring.main.web-application-type", "none");
        ConfigurableApplicationContext context =
                SpringApplication.run(EnergyAnalyzerApplication.class, args);
        CliRunner runner = context.getBean(CliRunner.class);
        int exitCode = runner.run(args);
        context.close();
        System.exit(exitCode);
    }
}
