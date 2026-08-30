package com.example.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({
        com.example.integration.config.HttpApiStorageProperties.class,
        com.example.integration.config.ApiCorsProperties.class
})
@SpringBootApplication
public class IntegrationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationEngineApplication.class, args);
    }
}
