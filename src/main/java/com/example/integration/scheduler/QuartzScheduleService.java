package com.example.integration.scheduler;

import com.example.integration.config.ScalableSchedulerProperties;
import com.example.integration.entity.ExecutionJob;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.enums.RuntimeRole;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.service.IntegrationOrchestrator;
import com.example.integration.service.execution.ExecutionJobService;
import com.example.integration.service.schedule.SchedulePlanningService;
import com.example.integration.service.schedule.SchedulerModeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuartzScheduleService {

    private final Scheduler scheduler;
    private final IntegrationDefinitionRepository integrationDefinitionRepository;
    private final SchedulePlanningService schedulePlanningService;
    private final SchedulerModeResolver schedulerModeResolver;
    private final ExecutionJobService executionJobService;
    private final IntegrationOrchestrator integrationOrchestrator;
    private final ScalableSchedulerProperties scalableSchedulerProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSchedules() {
        if (!scalableSchedulerProperties.hasRole(RuntimeRole.SCHEDULER)) {
            return;
        }

        integrationDefinitionRepository.findByEnabledTrue().stream()
                .filter(schedulerModeResolver::useQueueScheduling)
                .forEach(definition -> {
                    try {
                        refreshSchedule(definition);
                    } catch (Exception ex) {
                        log.error("Skipping Quartz schedule initialization for integrationId={} client={} due to invalid schedule configuration",
                                definition.getId(),
                                definition.getClientName(),
                                ex);
                    }
                });
    }

    public void refreshSchedule(IntegrationDefinition definition) {
        removeSchedule(definition.getId());
        if (!definition.isEnabled() || !schedulerModeResolver.useQueueScheduling(definition)) {
            return;
        }

        schedulePlanningService.resolveEnabledSchedules(definition)
                .forEach(plan -> schedule(definition, plan));
    }

    public void removeSchedule(Long integrationId) {
        try {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName(integrationId)))) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to remove Quartz schedule for integration " + integrationId, ex);
        }
    }

    public Long runScheduleNow(IntegrationDefinition definition, ScheduleType scheduleType, LocalDateTime triggerTime) {
        SchedulePlanningService.ResolvedSchedulePlan plan = schedulePlanningService.resolveEnabledSchedules(definition).stream()
                .filter(candidate -> candidate.scheduleType() == scheduleType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No enabled schedule config found for integration " + definition.getId() + " type " + scheduleType));

        ScheduleWindow scheduleWindow = schedulePlanningService.createWindow(
                definition,
                scheduleType,
                plan.scheduleIndex(),
                triggerTime);
        return integrationOrchestrator.runNow(definition.getId(), scheduleWindow);
    }

    public ExecutionJob enqueueTriggeredSchedule(
            Long integrationId,
            ScheduleType scheduleType,
            int scheduleIndex,
            LocalDateTime triggerTime) {
        IntegrationDefinition definition = integrationDefinitionRepository.findById(integrationId)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + integrationId));

        if (!definition.isEnabled() || !schedulerModeResolver.useQueueScheduling(definition)) {
            return null;
        }

        ExecutionJob job = executionJobService.enqueueScheduledRun(integrationId, scheduleType, scheduleIndex, triggerTime);
        log.info("Enqueued Quartz-triggered execution integrationId={} scheduleType={} scheduleIndex={} executionJobId={}",
                integrationId, scheduleType, scheduleIndex, job.getId());
        return job;
    }

    private void schedule(
            IntegrationDefinition definition,
            SchedulePlanningService.ResolvedSchedulePlan plan) {
        try {
            JobKey jobKey = jobKey(definition.getId(), plan.scheduleType(), plan.scheduleIndex());
            TriggerKey triggerKey = triggerKey(definition.getId(), plan.scheduleType(), plan.scheduleIndex());

            JobDetail jobDetail = JobBuilder.newJob(QuartzIntegrationEnqueueJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(QuartzIntegrationEnqueueJob.INTEGRATION_ID, definition.getId())
                    .usingJobData(QuartzIntegrationEnqueueJob.SCHEDULE_TYPE, plan.scheduleType().name())
                    .usingJobData(QuartzIntegrationEnqueueJob.SCHEDULE_INDEX, plan.scheduleIndex())
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(plan.cronExpression())
                            .inTimeZone(java.util.TimeZone.getDefault())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Quartz scheduled integration={} type={} index={} cron={}",
                    definition.getId(), plan.scheduleType(), plan.scheduleIndex(), plan.cronExpression());
        } catch (SchedulerException ex) {
            throw new IllegalStateException(
                    "Failed to schedule Quartz trigger for integration " + definition.getId(), ex);
        }
    }

    private String groupName(Long integrationId) {
        return "integration-" + integrationId;
    }

    private JobKey jobKey(Long integrationId, ScheduleType scheduleType, int scheduleIndex) {
        return JobKey.jobKey("job-" + schedulePlanningService.resolveScheduleKey(scheduleType, scheduleIndex), groupName(integrationId));
    }

    private TriggerKey triggerKey(Long integrationId, ScheduleType scheduleType, int scheduleIndex) {
        return TriggerKey.triggerKey("trigger-" + schedulePlanningService.resolveScheduleKey(scheduleType, scheduleIndex), groupName(integrationId));
    }
}
