package com.example.integration.service.execution;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.repository.ExecutionJobRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Primary
@ConditionalOnProperty(prefix = "integration.scalable", name = "job-store-vendor", havingValue = "MYSQL", matchIfMissing = true)
@RequiredArgsConstructor
public class MySqlExecutionJobStore implements ExecutionJobStore {

    private final ExecutionJobRepository executionJobRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public ExecutionJob enqueue(ExecutionJob job) {
        try {
            return executionJobRepository.save(job);
        } catch (DataIntegrityViolationException ex) {
            return executionJobRepository.findByDedupeKey(job.getDedupeKey())
                    .orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional
    public List<ExecutionJob> claimDueJobs(String workerId, int limit, LocalDateTime now, LocalDateTime leaseUntil) {
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager.createNativeQuery("""
                SELECT id
                FROM execution_job
                WHERE status IN ('PENDING', 'RETRY_WAIT')
                  AND next_attempt_at <= :now
                  AND (lease_until IS NULL OR lease_until < :now)
                ORDER BY planned_fire_at ASC, id ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .setParameter("now", Timestamp.valueOf(now))
                .setParameter("limit", limit)
                .getResultList();

        if (ids.isEmpty()) {
            return List.of();
        }

        List<Long> claimedIds = new ArrayList<>(ids.size());
        for (Number id : ids) {
            claimedIds.add(id.longValue());
        }

        List<ExecutionJob> jobs = executionJobRepository.findAllById(claimedIds);
        jobs.forEach(job -> {
            job.setStatus(ExecutionJobStatus.CLAIMED);
            job.setWorkerId(workerId);
            job.setClaimedAt(now);
            job.setLeaseUntil(leaseUntil);
        });
        executionJobRepository.saveAll(jobs);
        jobs.sort(Comparator.comparing(ExecutionJob::getPlannedFireAt).thenComparing(ExecutionJob::getId));
        return jobs;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExecutionJob> findById(Long id) {
        return executionJobRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionJob> findAll() {
        return executionJobRepository.findAll().stream()
                .sorted(Comparator.comparing(ExecutionJob::getPlannedFireAt, Comparator.reverseOrder())
                        .thenComparing(ExecutionJob::getId, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionJob> findByIntegrationId(Long integrationId) {
        return executionJobRepository.findByIntegrationIdOrderByPlannedFireAtDesc(integrationId);
    }

    @Override
    @Transactional
    public void markRunning(Long jobId, String workerId, LocalDateTime startedAt, LocalDateTime leaseUntil) {
        ExecutionJob job = getRequired(jobId);
        job.setStatus(ExecutionJobStatus.RUNNING);
        job.setWorkerId(workerId);
        job.setStartedAt(startedAt);
        job.setLeaseUntil(leaseUntil);
        executionJobRepository.save(job);
    }

    @Override
    @Transactional
    public void extendLease(Long jobId, String workerId, LocalDateTime leaseUntil) {
        ExecutionJob job = getRequired(jobId);
        if (job.getWorkerId() == null || !job.getWorkerId().equals(workerId)) {
            return;
        }
        job.setLeaseUntil(leaseUntil);
        executionJobRepository.save(job);
    }

    @Override
    @Transactional
    public void markSuccess(Long jobId, String workerId, LocalDateTime finishedAt) {
        ExecutionJob job = getRequired(jobId);
        job.setStatus(ExecutionJobStatus.SUCCESS);
        job.setWorkerId(workerId);
        job.setFinishedAt(finishedAt);
        job.setLeaseUntil(null);
        executionJobRepository.save(job);
    }

    @Override
    @Transactional
    public void markNoData(Long jobId, String workerId, FailureCategory failureCategory, String errorMessage, LocalDateTime finishedAt) {
        ExecutionJob job = getRequired(jobId);
        job.setStatus(ExecutionJobStatus.NO_DATA);
        job.setWorkerId(workerId);
        job.setFinishedAt(finishedAt);
        job.setLeaseUntil(null);
        job.setLastFailureCategory(failureCategory);
        job.setLastError(errorMessage);
        executionJobRepository.save(job);
    }

    @Override
    @Transactional
    public void scheduleRetry(
            Long jobId,
            String workerId,
            int nextAttemptCount,
            FailureCategory failureCategory,
            String failedStepName,
            Integer httpStatus,
            String errorMessage,
            LocalDateTime nextAttemptAt) {
        ExecutionJob job = getRequired(jobId);
        job.setStatus(ExecutionJobStatus.RETRY_WAIT);
        job.setWorkerId(workerId);
        job.setAttemptCount(nextAttemptCount);
        job.setNextAttemptAt(nextAttemptAt);
        job.setLeaseUntil(null);
        job.setLastFailureCategory(failureCategory);
        job.setLastFailedStepName(failedStepName);
        job.setLastHttpStatus(httpStatus);
        job.setLastError(errorMessage);
        executionJobRepository.save(job);
    }

    @Override
    @Transactional
    public void markTerminalFailure(
            Long jobId,
            String workerId,
            ExecutionJobStatus terminalStatus,
            FailureCategory failureCategory,
            String failedStepName,
            Integer httpStatus,
            String errorMessage,
            LocalDateTime finishedAt) {
        ExecutionJob job = getRequired(jobId);
        job.setStatus(terminalStatus);
        job.setWorkerId(workerId);
        job.setFinishedAt(finishedAt);
        job.setLeaseUntil(null);
        job.setLastFailureCategory(failureCategory);
        job.setLastFailedStepName(failedStepName);
        job.setLastHttpStatus(httpStatus);
        job.setLastError(errorMessage);
        executionJobRepository.save(job);
    }

    @Override
    @Transactional
    public void recoverExpiredLeases(LocalDateTime now) {
        List<ExecutionJob> stuckJobs = executionJobRepository.findByStatusInAndLeaseUntilBefore(
                List.of(ExecutionJobStatus.CLAIMED, ExecutionJobStatus.RUNNING),
                now);

        for (ExecutionJob job : stuckJobs) {
            if (job.getStatus() == ExecutionJobStatus.RUNNING) {
                job.setStatus(ExecutionJobStatus.RETRY_WAIT);
                job.setNextAttemptAt(now);
            } else {
                job.setStatus(ExecutionJobStatus.PENDING);
            }
            job.setWorkerId(null);
            job.setLeaseUntil(null);
            job.setClaimedAt(null);
            executionJobRepository.save(job);
        }
    }

    private ExecutionJob getRequired(Long id) {
        return executionJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Execution job not found: " + id));
    }
}
