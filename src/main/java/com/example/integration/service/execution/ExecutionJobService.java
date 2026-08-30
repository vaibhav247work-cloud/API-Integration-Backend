package com.example.integration.service.execution;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.service.schedule.SchedulePlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ExecutionJobService {

    private final IntegrationDefinitionRepository integrationDefinitionRepository;
    private final ExecutionJobStore executionJobStore;
    private final ExecutionJobMetrics executionJobMetrics;
    private final SchedulePlanningService schedulePlanningService;

    public ExecutionJob enqueueRun(Long integrationId) {
        LocalDateTime now = LocalDateTime.now();
        return enqueue(integrationId, ScheduleWindow.adHoc(now), now);
    }

    public ExecutionJob enqueue(Long integrationId, ScheduleWindow scheduleWindow, LocalDateTime plannedFireAt) {
        IntegrationDefinition definition = findDefinition(integrationId);
        ExecutionJob job = new ExecutionJob();
        job.setIntegrationId(definition.getId());
        job.setDedupeKey(buildDedupeKey(definition.getId(), scheduleWindow, plannedFireAt));
        job.setScheduleType(scheduleWindow.getScheduleType());
        job.setPlannedFireAt(plannedFireAt);
        job.setWindowStart(scheduleWindow.getWindowStart());
        job.setWindowEnd(scheduleWindow.getWindowEnd());
        job.setFileToken(scheduleWindow.getFileToken());
        job.setStatus(ExecutionJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(definition.getMaxRetries());
        job.setNextAttemptAt(plannedFireAt);

        ExecutionJob saved = executionJobStore.enqueue(job);
        executionJobMetrics.recordEnqueued();
        return saved;
    }

    public ExecutionJob enqueueScheduledRun(
            Long integrationId,
            ScheduleType scheduleType,
            int scheduleIndex,
            LocalDateTime triggerTime) {
        IntegrationDefinition definition = findDefinition(integrationId);
        ScheduleWindow scheduleWindow = schedulePlanningService.createWindow(definition, scheduleType, scheduleIndex, triggerTime);
        return enqueue(definition.getId(), scheduleWindow, triggerTime);
    }

    public ExecutionJob findJob(Long jobId) {
        return executionJobStore.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Execution job not found: " + jobId));
    }

    public List<ExecutionJob> findJobs(
            Long integrationId,
            List<ExecutionJobStatus> statuses,
            LocalDateTime fromTime,
            LocalDateTime toTime) {
        List<ExecutionJob> jobs = integrationId == null
                ? executionJobStore.findAll()
                : executionJobStore.findByIntegrationId(integrationId);

        return jobs.stream()
                .filter(job -> statuses == null || statuses.isEmpty() || statuses.contains(job.getStatus()))
                .filter(job -> fromTime == null || !job.getPlannedFireAt().isBefore(fromTime))
                .filter(job -> toTime == null || !job.getPlannedFireAt().isAfter(toTime))
                .toList();
    }

    private IntegrationDefinition findDefinition(Long integrationId) {
        return integrationDefinitionRepository.findById(integrationId)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + integrationId));
    }

    private String buildDedupeKey(Long integrationId, ScheduleWindow scheduleWindow, LocalDateTime plannedFireAt) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(String.valueOf(integrationId));
        joiner.add(scheduleWindow.getScheduleType().name());
        joiner.add(scheduleWindow.getWindowStart() == null ? "na" : scheduleWindow.getWindowStart().toString());
        joiner.add(scheduleWindow.getWindowEnd() == null ? "na" : scheduleWindow.getWindowEnd().toString());
        joiner.add(scheduleWindow.getFileToken() == null ? "na" : scheduleWindow.getFileToken());
        if (scheduleWindow.getScheduleType() == ScheduleType.AD_HOC || scheduleWindow.getScheduleType() == ScheduleType.CUSTOM) {
            joiner.add(plannedFireAt.toString());
        }
        return joiner.toString();
    }
}
