package com.example.integration.entity;

import com.example.integration.entity.converter.JsonNodeStringConverter;
import com.example.integration.model.enums.IntegrationSchedulerMode;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "integration_definition")
public class IntegrationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "brand_code")
    private String brandCode;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "schedule_cron")
    private String scheduleCron;

    @Convert(converter = JsonNodeStringConverter.class)
    @Column(name = "schedule_config", columnDefinition = "LONGTEXT")
    private JsonNode scheduleConfig;

    @Column(name = "csv_file_name", nullable = false)
    private String csvFileName;

    @Column(name = "output_directory")
    private String outputDirectory;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Convert(converter = JsonNodeStringConverter.class)
    @Column(name = "auth_config", columnDefinition = "LONGTEXT")
    private JsonNode authConfig;

    @Convert(converter = JsonNodeStringConverter.class)
    @Column(name = "request_config", columnDefinition = "LONGTEXT")
    private JsonNode requestConfig;

    @Convert(converter = JsonNodeStringConverter.class)
    @Column(name = "response_config", columnDefinition = "LONGTEXT")
    private JsonNode responseConfig;

    @Convert(converter = JsonNodeStringConverter.class)
    @Column(name = "pagination_config", columnDefinition = "LONGTEXT")
    private JsonNode paginationConfig;

    @Convert(converter = JsonNodeStringConverter.class)
    @Column(name = "storage_config", columnDefinition = "LONGTEXT")
    private JsonNode storageConfig;

    @Convert(converter = JsonNodeStringConverter.class)
    @Column(name = "step_config", columnDefinition = "LONGTEXT")
    private JsonNode stepConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheduler_mode", nullable = false, length = 20)
    private IntegrationSchedulerMode schedulerMode = IntegrationSchedulerMode.LEGACY;

    @JsonManagedReference
    @OrderBy("sortOrder ASC")
    @OneToMany(mappedBy = "integrationDefinition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<IntegrationFieldMapping> fieldMappings = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void replaceMappings(List<IntegrationFieldMapping> mappings) {
        fieldMappings.clear();
        if (mappings == null) {
            return;
        }
        mappings.forEach(this::addMapping);
    }

    public void addMapping(IntegrationFieldMapping mapping) {
        mapping.setIntegrationDefinition(this);
        fieldMappings.add(mapping);
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
