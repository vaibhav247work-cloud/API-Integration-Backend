package com.example.integration.entity;

import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.ScheduleType;
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
@Table(name = "execution_job")
public class ExecutionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_id", nullable = false)
    private Long integrationId;

    @Column(name = "dedupe_key", nullable = false, length = 255, unique = true)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 40)
    private ScheduleType scheduleType;

    @Column(name = "planned_fire_at", nullable = false)
    private LocalDateTime plannedFireAt;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "window_end")
    private LocalDateTime windowEnd;

    @Column(name = "file_token", length = 100)
    private String fileToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutionJobStatus status = ExecutionJobStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "worker_id", length = 120)
    private String workerId;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_category", length = 50)
    private FailureCategory lastFailureCategory;

    @Column(name = "last_failed_step_name")
    private String lastFailedStepName;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (nextAttemptAt == null) {
            nextAttemptAt = plannedFireAt == null ? now : plannedFireAt;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
