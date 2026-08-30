package com.example.integration.service.schedule;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.service.ConfigBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulePlanningService {

    private final ConfigBindingService configBindingService;
    private final List<ScheduleStrategy> scheduleStrategies;

    public List<ResolvedSchedulePlan> resolveEnabledSchedules(IntegrationDefinition definition) {
        List<ScheduleDefinition> scheduleDefinitions = configBindingService.getScheduleDefinitions(definition);
        if (!scheduleDefinitions.isEmpty()) {
            List<ResolvedSchedulePlan> plans = new ArrayList<>();
            for (int index = 0; index < scheduleDefinitions.size(); index++) {
                ScheduleDefinition scheduleDefinition = scheduleDefinitions.get(index);
                if (Boolean.FALSE.equals(scheduleDefinition.getEnabled())) {
                    continue;
                }
                plans.add(new ResolvedSchedulePlan(
                        definition.getId(),
                        index,
                        scheduleDefinition.getType(),
                        scheduleDefinition,
                        resolveCronExpression(definition.getId(), index, scheduleDefinition)));
            }
            return plans;
        }

        if (!StringUtils.hasText(definition.getScheduleCron())) {
            return List.of();
        }

        ScheduleDefinition legacyCron = new ScheduleDefinition();
        legacyCron.setType(ScheduleType.LEGACY_CRON);
        legacyCron.setCronExpression(definition.getScheduleCron());
        legacyCron.setEnabled(true);

        return List.of(new ResolvedSchedulePlan(
                definition.getId(),
                0,
                ScheduleType.LEGACY_CRON,
                legacyCron,
                normalizeQuartzCronExpression(definition.getScheduleCron())));
    }

    public ScheduleWindow createWindow(
            IntegrationDefinition definition,
            ScheduleType scheduleType,
            int scheduleIndex,
            LocalDateTime triggerTime) {
        ResolvedSchedulePlan plan = resolvePlan(definition, scheduleType, scheduleIndex);
        if (scheduleType == ScheduleType.LEGACY_CRON) {
            return ScheduleWindow.legacyCron(triggerTime);
        }
        return resolveStrategy(scheduleType).createWindow(triggerTime, plan.scheduleDefinition());
    }

    public ScheduleWindow createWindow(
            IntegrationDefinition definition,
            ScheduleType scheduleType,
            LocalDateTime triggerTime) {
        ResolvedSchedulePlan plan = resolveEnabledSchedules(definition).stream()
                .filter(candidate -> candidate.scheduleType() == scheduleType)
                .findFirst()
                .orElseThrow(() -> invalid(
                        "No enabled scheduleConfig entry found for type "
                                + scheduleType + " on integration " + definition.getId()));
        if (scheduleType == ScheduleType.LEGACY_CRON) {
            return ScheduleWindow.legacyCron(triggerTime);
        }
        return resolveStrategy(scheduleType).createWindow(triggerTime, plan.scheduleDefinition());
    }

    public ResolvedSchedulePlan resolvePlan(
            IntegrationDefinition definition,
            ScheduleType scheduleType,
            int scheduleIndex) {
        return resolveEnabledSchedules(definition).stream()
                .filter(candidate -> candidate.scheduleType() == scheduleType)
                .filter(candidate -> candidate.scheduleIndex() == scheduleIndex)
                .findFirst()
                .orElseThrow(() -> invalid(
                        "No enabled scheduleConfig entry found for type "
                                + scheduleType + " index " + scheduleIndex
                                + " on integration " + definition.getId()));
    }

    public String resolveScheduleKey(ScheduleType scheduleType, int scheduleIndex) {
        return scheduleType.name() + ":" + scheduleIndex;
    }

    private ScheduleStrategy resolveStrategy(ScheduleType scheduleType) {
        Map<ScheduleType, ScheduleStrategy> strategyMap = scheduleStrategies.stream()
                .collect(Collectors.toMap(ScheduleStrategy::getType, Function.identity()));
        ScheduleStrategy scheduleStrategy = strategyMap.get(scheduleType);
        if (scheduleStrategy == null) {
            throw invalid("No schedule strategy registered for " + scheduleType);
        }
        return scheduleStrategy;
    }

    private String resolveCronExpression(Long integrationId, int scheduleIndex, ScheduleDefinition definition) {
        if (definition.getType() == ScheduleType.LEGACY_CRON) {
            return normalizeQuartzCronExpression(definition.getCronExpression());
        }
        if (StringUtils.hasText(definition.getCronExpression())) {
            return normalizeQuartzCronExpression(definition.getCronExpression());
        }

        int seed = Math.floorMod(Objects.hash(integrationId, definition.getType(), scheduleIndex), Integer.MAX_VALUE);
        return switch (definition.getType()) {
            case HOURLY -> {
                int interval = definition.getIntervalHours() == null || definition.getIntervalHours() < 1
                        ? 1
                        : definition.getIntervalHours();
                int minute = seed % 60;
                yield "0 " + minute + " 0/" + interval + " * * ?";
            }
            case DAILY -> {
                int minute = seed % 60;
                int hour = (seed / 60) % 24;
                yield "0 " + minute + " " + hour + " * * ?";
            }
            case MONTHLY -> {
                int minute = seed % 60;
                int hour = (seed / 60) % 24;
                yield "0 " + minute + " " + hour + " 1 * ?";
            }
            default -> throw invalid("Unsupported schedule type for Quartz plan: " + definition.getType());
        };
    }

    String normalizeQuartzCronExpression(String cronExpression) {
        if (!StringUtils.hasText(cronExpression)) {
            return cronExpression;
        }

        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length != 6 && parts.length != 7) {
            return cronExpression.trim();
        }

        int dayOfMonthIndex = 3;
        int dayOfWeekIndex = 5;
        String dayOfMonth = parts[dayOfMonthIndex];
        String dayOfWeek = parts[dayOfWeekIndex];

        if ("*".equals(dayOfMonth) && "*".equals(dayOfWeek)) {
            parts[dayOfWeekIndex] = "?";
        } else if ("*".equals(dayOfMonth) && !"?".equals(dayOfWeek)) {
            parts[dayOfMonthIndex] = "?";
        } else if ("*".equals(dayOfWeek) && !"?".equals(dayOfMonth)) {
            parts[dayOfWeekIndex] = "?";
        } else if (!"?".equals(dayOfMonth) && !"?".equals(dayOfWeek)) {
            parts[dayOfWeekIndex] = "?";
        }

        return String.join(" ", parts);
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

    public record ResolvedSchedulePlan(
            Long integrationId,
            int scheduleIndex,
            ScheduleType scheduleType,
            ScheduleDefinition scheduleDefinition,
            String cronExpression) {
    }
}
