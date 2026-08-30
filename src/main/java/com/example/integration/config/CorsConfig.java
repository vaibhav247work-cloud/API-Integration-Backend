package com.example.integration.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final ApiCorsProperties apiCorsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(apiCorsProperties.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(apiCorsProperties.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(apiCorsProperties.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(apiCorsProperties.getExposedHeaders().toArray(String[]::new))
                .allowCredentials(apiCorsProperties.isAllowCredentials())
                .maxAge(apiCorsProperties.getMaxAgeSeconds());
    }
}
