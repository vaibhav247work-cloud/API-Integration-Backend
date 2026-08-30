package com.example.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.cors")
public class ApiCorsProperties {

    private List<String> allowedOriginPatterns = new ArrayList<>();
    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    private List<String> exposedHeaders = new ArrayList<>(List.of("Location"));
    private boolean allowCredentials = true;
    private long maxAgeSeconds = 3600;
}
