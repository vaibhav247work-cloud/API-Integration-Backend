package com.example.integration.entity;

import com.example.integration.model.enums.MappingType;
import com.example.integration.model.enums.PathType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "integration_field_mapping")
public class IntegrationFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private IntegrationDefinition integrationDefinition;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false, length = 40)
    private MappingType mappingType = MappingType.SOURCE_PATH;

    @Column(name = "source_path", length = 2000)
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "path_type", length = 40)
    private PathType pathType = PathType.JSON_PATH;

    @Column(name = "target_header", nullable = false)
    private String targetHeader;

    @Column(name = "expression", length = 4000)
    private String expression;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "formatter")
    private String formatter;

    @Column(name = "required_flag", nullable = false)
    private boolean requiredFlag;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
