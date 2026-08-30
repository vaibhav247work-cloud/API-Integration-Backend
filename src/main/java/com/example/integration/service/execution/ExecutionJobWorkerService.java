package com.example.integration.service.execution;

import com.example.integration.config.ScalableSchedulerProperties;
import com.example.integration.entity.ExecutionJob;
import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.RuntimeRole;
import com.example.integration.service.IntegrationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionJobWorkerService {

    private final ScalableSchedulerProperties scalableSchedulerProperties;
    private final ExecutionJobStore executionJobStore;
    private final ExecutionJobMetrics executionJobMetrics;
    private final IntegrationOrchestrator integrationOrchestrator;
    private final ThreadPoolTaskScheduler integrationTaskScheduler;
    private final AtomicInteger inFlightJobs = new AtomicInteger();

    @Scheduled(fixedDelayString = "${integration.scalable.worker.poll-interval-ms:5000}")
    public void poll() {
        if (!scalableSchedulerProperties.hasRole(RuntimeRole.WORKER)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        executionJobStore.recoverExpiredLeases(now);
        int availableCapacity = Math.max(0, scalableSchedulerProperties.getWorker().getMaxConcurrency() - inFlightJobs.get());
        if (availableCapacity <= 0) {
            return;
        }

        List<ExecutionJob> jobs = executionJobStore.claimDueJobs(
                workerId(),
                Math.min(scalableSchedulerProperties.getWorker().getBatchSize(), availableCapacity),
                now,
                now.plusSeconds(scalableSchedulerProperties.getWorker().getLeaseSeconds()));

        if (jobs.isEmpty()) {
            return;
        }

        executionJobMetrics.recordClaimed(jobs.size());
        jobs.forEach(job -> {
            inFlightJobs.incrementAndGet();
            processJobAsync(job.getId());
        });
    }

    @Async("executionWorkerExecutor")
    public CompletableFuture<Void> processJobAsync(Long jobId) {
        LocalDateTime startedAt = LocalDateTime.now();
        String workerId = workerId();
        executionJobStore.markRunning(
                jobId,
                workerId,
                startedAt,
                startedAt.plusSeconds(scalableSchedulerProperties.getWorker().getLeaseSeconds()));

        ScheduledFuture<?> heartbeat = integrationTaskScheduler.scheduleAtFixedRate(
                () -> executionJobStore.extendLease(
                        jobId,
                        workerId,
                        LocalDateTime.now().plusSeconds(scalableSchedulerProperties.getWorker().getLeaseSeconds())),
                Duration.ofSeconds(Math.max(5, scalableSchedulerProperties.getWorker().getHeartbeatSeconds())));

        try {
            integrationOrchestrator.runQueuedJob(jobId, workerId);
        } catch (Exception ex) {
            log.error("Queued execution failed unexpectedly jobId={} workerId={} message={}", jobId, workerId, ex.getMessage(), ex);
            executionJobStore.markTerminalFailure(
                    jobId,
                    workerId,
                    ExecutionJobStatus.DEAD,
                    FailureCategory.UNKNOWN_ERROR,
                    null,
                    null,
                    ex.getMessage(),
                    LocalDateTime.now());
        } finally {
            heartbeat.cancel(false);
            inFlightJobs.decrementAndGet();
        }

        return CompletableFuture.completedFuture(null);
    }

    private String workerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ProcessHandle.current().pid();
        } catch (Exception ignored) {
            return "worker:" + ProcessHandle.current().pid();
        }
    }
}
