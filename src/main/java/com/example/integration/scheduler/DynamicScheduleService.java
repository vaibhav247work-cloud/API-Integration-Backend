package com.example.integration.scheduler;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.service.ConfigBindingService;
import com.example.integration.service.IntegrationOrchestrator;
import com.example.integration.service.schedule.ScheduleStrategy;
import com.example.integration.service.schedule.SchedulerModeResolver;
import com.example.integration.config.ScalableSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class DynamicScheduleService {

    private final ThreadPoolTaskScheduler integrationTaskScheduler;
    private final IntegrationDefinitionRepository integrationDefinitionRepository;
    private final ConfigBindingService configBindingService;
    private final IntegrationOrchestrator integrationOrchestrator;
    private final List<ScheduleStrategy> scheduleStrategies;
    private final SchedulerModeResolver schedulerModeResolver;

    @Autowired
    public DynamicScheduleService(
            ThreadPoolTaskScheduler integrationTaskScheduler,
            IntegrationDefinitionRepository integrationDefinitionRepository,
            ConfigBindingService configBindingService,
            IntegrationOrchestrator integrationOrchestrator,
            List<ScheduleStrategy> scheduleStrategies,
            SchedulerModeResolver schedulerModeResolver) {
        this.integrationTaskScheduler = integrationTaskScheduler;
        this.integrationDefinitionRepository = integrationDefinitionRepository;
        this.configBindingService = configBindingService;
        this.integrationOrchestrator = integrationOrchestrator;
        this.scheduleStrategies = scheduleStrategies;
        this.schedulerModeResolver = schedulerModeResolver;
    }

    DynamicScheduleService(
            ThreadPoolTaskScheduler integrationTaskScheduler,
            IntegrationDefinitionRepository integrationDefinitionRepository,
            ConfigBindingService configBindingService,
            IntegrationOrchestrator integrationOrchestrator,
            List<ScheduleStrategy> scheduleStrategies) {
        this(
                integrationTaskScheduler,
                integrationDefinitionRepository,
                configBindingService,
                integrationOrchestrator,
                scheduleStrategies,
                new SchedulerModeResolver(new ScalableSchedulerProperties()));
    }

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSchedules() {
        integrationDefinitionRepository.findByEnabledTrue()
                .stream()
                .filter(definition -> !schedulerModeResolver.useQueueScheduling(definition))
                .forEach(this::refreshSchedule);
    }

    public void refreshSchedule(IntegrationDefinition definition) {
        removeSchedule(definition.getId());
        if (!definition.isEnabled() || schedulerModeResolver.useQueueScheduling(definition)) {
            return;
        }

        Map<ScheduleType, ScheduleStrategy> strategyMap = scheduleStrategyMap();
        List<ScheduleDefinition> scheduleDefinitions = configBindingService.getScheduleDefinitions(definition);

        if (!scheduleDefinitions.isEmpty()) {
            scheduleDefinitions.stream()
                    .filter(scheduleDefinition -> !Boolean.FALSE.equals(scheduleDefinition.getEnabled()))
                    .forEach(scheduleDefinition -> schedule(scheduleDefinition, definition, strategyMap));
            return;
        }

        if (!StringUtils.hasText(definition.getScheduleCron())) {
            return;
        }

        String taskKey = taskKey(definition.getId(), ScheduleType.LEGACY_CRON.name());
        ScheduledFuture<?> scheduledFuture = integrationTaskScheduler.schedule(
                () -> integrationOrchestrator.runAsync(
                        definition.getId(),
                        ScheduleWindow.legacyCron(LocalDateTime.now())),
                new CronTrigger(definition.getScheduleCron(), ZoneId.systemDefault()));

        scheduledTasks.put(taskKey, scheduledFuture);
        log.info("Scheduled integration {} with legacy cron {}", definition.getId(), definition.getScheduleCron());
    }

    public void removeSchedule(Long integrationId) {
        String prefix = integrationId + ":";
        scheduledTasks.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) {
                return false;
            }
            entry.getValue().cancel(false);
            return true;
        });
    }

    public Long runScheduleNow(Long integrationId, ScheduleType scheduleType) {
        return runScheduleNow(integrationId, scheduleType, LocalDateTime.now());
    }

    public Long runScheduleNow(Long integrationId, ScheduleType scheduleType, LocalDateTime triggerTime) {
        IntegrationDefinition definition = integrationDefinitionRepository.findById(integrationId)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + integrationId));

        ScheduleDefinition scheduleDefinition = resolveScheduleDefinition(definition, scheduleType);
        ScheduleStrategy scheduleStrategy = resolveScheduleStrategy(scheduleType, scheduleStrategyMap());
        ScheduleWindow scheduleWindow = scheduleStrategy.createWindow(triggerTime, scheduleDefinition);

        return integrationOrchestrator.runNow(integrationId, scheduleWindow);
    }

    private void schedule(
            ScheduleDefinition scheduleDefinition,
            IntegrationDefinition definition,
            Map<ScheduleType, ScheduleStrategy> strategyMap) {
        ScheduleStrategy scheduleStrategy = resolveScheduleStrategy(scheduleDefinition.getType(), strategyMap);

        String taskKey = taskKey(definition.getId(), scheduleDefinition.getType().name());
        ScheduledFuture<?> scheduledFuture = integrationTaskScheduler.schedule(
                () -> integrationOrchestrator.runAsync(
                        definition.getId(),
                        scheduleStrategy.createWindow(LocalDateTime.now(), scheduleDefinition)),
                scheduleStrategy.buildTrigger(scheduleDefinition));

        scheduledTasks.put(taskKey, scheduledFuture);
        log.info("Scheduled integration {} with type {}", definition.getId(), scheduleDefinition.getType());
    }

    private String taskKey(Long integrationId, String scheduleKey) {
        return integrationId + ":" + scheduleKey;
    }

    private Map<ScheduleType, ScheduleStrategy> scheduleStrategyMap() {
        return scheduleStrategies.stream()
                .collect(Collectors.toMap(ScheduleStrategy::getType, Function.identity()));
    }

    private ScheduleStrategy resolveScheduleStrategy(
            ScheduleType scheduleType,
            Map<ScheduleType, ScheduleStrategy> strategyMap) {
        ScheduleStrategy scheduleStrategy = strategyMap.get(scheduleType);
        if (scheduleStrategy == null) {
            throw invalid("No schedule strategy registered for " + scheduleType);
        }
        return scheduleStrategy;
    }

    private ScheduleDefinition resolveScheduleDefinition(IntegrationDefinition definition, ScheduleType scheduleType) {
        return configBindingService.getScheduleDefinitions(definition).stream()
                .filter(scheduleDefinition -> !Boolean.FALSE.equals(scheduleDefinition.getEnabled()))
                .filter(scheduleDefinition -> scheduleDefinition.getType() == scheduleType)
                .findFirst()
                .orElseThrow(() -> invalid(
                        "No enabled scheduleConfig entry found for type "
                                + scheduleType + " on integration " + definition.getId()));
    }

    private IntegrationFailureException invalid(String message) {
        return new IntegrationFailureException(
                FailureCategory.CONFIGURATION_ERROR,
                message,
                "SCHEDULE_CONFIG",
                null,
                null,
                null,
                false);
    }
}
