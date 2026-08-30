package com.example.integration.service.schedule;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.scheduler.DynamicScheduleService;
import com.example.integration.scheduler.QuartzScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IntegrationSchedulingService {

    private final IntegrationDefinitionRepository integrationDefinitionRepository;
    private final DynamicScheduleService dynamicScheduleService;
    private final QuartzScheduleService quartzScheduleService;
    private final SchedulerModeResolver schedulerModeResolver;
    private final SchedulePlanningService schedulePlanningService;

    public void refreshSchedule(IntegrationDefinition definition) {
        if (schedulerModeResolver.useQueueScheduling(definition)) {
            dynamicScheduleService.removeSchedule(definition.getId());
            quartzScheduleService.refreshSchedule(definition);
            return;
        }

        quartzScheduleService.removeSchedule(definition.getId());
        dynamicScheduleService.refreshSchedule(definition);
    }

    public void removeSchedule(Long integrationId) {
        dynamicScheduleService.removeSchedule(integrationId);
        quartzScheduleService.removeSchedule(integrationId);
    }

    public Long runScheduleNow(Long integrationId, ScheduleType scheduleType) {
        return runScheduleNow(integrationId, scheduleType, LocalDateTime.now());
    }

    public Long runScheduleNow(Long integrationId, ScheduleType scheduleType, LocalDateTime triggerTime) {
        IntegrationDefinition definition = integrationDefinitionRepository.findById(integrationId)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + integrationId));

        if (schedulerModeResolver.useQueueScheduling(definition)) {
            return quartzScheduleService.runScheduleNow(definition, scheduleType, triggerTime);
        }

        return dynamicScheduleService.runScheduleNow(integrationId, scheduleType, triggerTime);
    }

    public SchedulePlanningService.ResolvedSchedulePlan resolvePlan(
            IntegrationDefinition definition,
            ScheduleType scheduleType,
            int scheduleIndex) {
        return schedulePlanningService.resolvePlan(definition, scheduleType, scheduleIndex);
    }
}
