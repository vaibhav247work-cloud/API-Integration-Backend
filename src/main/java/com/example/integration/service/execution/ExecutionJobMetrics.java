package com.example.integration.service.execution;

import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.repository.ExecutionJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Component
public class ExecutionJobMetrics {

    private final Counter enqueuedCounter;
    private final Counter retriedCounter;
    private final Counter claimedCounter;
    private final Counter recoveredLeaseCounter;
    private final Map<ExecutionJobStatus, Counter> statusCounters = new EnumMap<>(ExecutionJobStatus.class);
    private final MeterRegistry meterRegistry;

    public ExecutionJobMetrics(MeterRegistry meterRegistry, ExecutionJobRepository executionJobRepository) {
        this.meterRegistry = meterRegistry;
        this.enqueuedCounter = meterRegistry.counter("integration.execution.enqueued.total");
        this.retriedCounter = meterRegistry.counter("integration.execution.retry.total");
        this.claimedCounter = meterRegistry.counter("integration.execution.claim.total");
        this.recoveredLeaseCounter = meterRegistry.counter("integration.execution.lease.recovered.total");

        for (ExecutionJobStatus status : ExecutionJobStatus.values()) {
            statusCounters.put(status, meterRegistry.counter(
                    "integration.execution.completed.total",
                    "status",
                    status.name()));

            Gauge.builder(
                            "integration.execution.jobs.depth",
                            executionJobRepository,
                            repository -> repository.countByStatus(status))
                    .description("Execution-job queue depth by status")
                    .tag("status", status.name())
                    .register(meterRegistry);
        }
    }

    public void recordEnqueued() {
        enqueuedCounter.increment();
    }

    public void recordClaimed(int count) {
        claimedCounter.increment(count);
    }

    public void recordRetried() {
        retriedCounter.increment();
    }

    public void recordLeaseRecovered() {
        recoveredLeaseCounter.increment();
    }

    public void recordCompletion(ExecutionJobStatus status, Duration duration) {
        statusCounters.get(status).increment();
        if (duration != null && !duration.isNegative()) {
            Timer.builder("integration.execution.duration")
                    .tag("status", status.name())
                    .register(meterRegistry)
                    .record(duration);
        }
    }
}
