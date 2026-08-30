package com.example.integration.service.execution;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.model.enums.FailureCategory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExecutionJobStore {

    ExecutionJob enqueue(ExecutionJob job);

    List<ExecutionJob> claimDueJobs(String workerId, int limit, LocalDateTime now, LocalDateTime leaseUntil);

    Optional<ExecutionJob> findById(Long id);

    List<ExecutionJob> findAll();

    List<ExecutionJob> findByIntegrationId(Long integrationId);

    void markRunning(Long jobId, String workerId, LocalDateTime startedAt, LocalDateTime leaseUntil);

    void extendLease(Long jobId, String workerId, LocalDateTime leaseUntil);

    void markSuccess(Long jobId, String workerId, LocalDateTime finishedAt);

    void markNoData(Long jobId, String workerId, FailureCategory failureCategory, String errorMessage, LocalDateTime finishedAt);

    void scheduleRetry(
            Long jobId,
            String workerId,
            int nextAttemptCount,
            FailureCategory failureCategory,
            String failedStepName,
            Integer httpStatus,
            String errorMessage,
            LocalDateTime nextAttemptAt);

    void markTerminalFailure(
            Long jobId,
            String workerId,
            ExecutionJobStatus terminalStatus,
            FailureCategory failureCategory,
            String failedStepName,
            Integer httpStatus,
            String errorMessage,
            LocalDateTime finishedAt);

    void recoverExpiredLeases(LocalDateTime now);
}
