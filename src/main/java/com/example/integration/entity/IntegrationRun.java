package com.example.integration.entity;

import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.enums.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "integration_run")
public class IntegrationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_id", nullable = false)
    private Long integrationId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "correlation_id", nullable = false, length = 80)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RunStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", length = 40)
    private ScheduleType scheduleType;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "window_end")
    private LocalDateTime windowEnd;

    @Column(name = "file_token", length = 100)
    private String fileToken;

    @Column(name = "execution_job_id")
    private Long executionJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 50)
    private FailureCategory failureCategory;

    @Column(name = "failed_step_name")
    private String failedStepName;

    @Column(name = "failed_request_url", length = 1000)
    private String failedRequestUrl;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "response_preview", columnDefinition = "TEXT")
    private String responsePreview;

    @Column(name = "output_location")
    private String outputLocation;

    @Column(name = "records_processed", nullable = false)
    private long recordsProcessed;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

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
