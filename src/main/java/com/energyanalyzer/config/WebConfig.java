package com.energyanalyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig
 * Spring MVC configuration for the Energy Analyzer web application.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Spring Boot auto-configures multipart and session from application.yml.
    // This class is a placeholder for any future MVC customisations
    // (CORS, interceptors, resource handlers, etc.)
}
