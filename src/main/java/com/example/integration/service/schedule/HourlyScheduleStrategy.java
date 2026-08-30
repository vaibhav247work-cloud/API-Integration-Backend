package com.example.integration.service.schedule;

import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
public class HourlyScheduleStrategy implements ScheduleStrategy {

    private static final DateTimeFormatter FILE_TOKEN = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Override
    public ScheduleType getType() {
        return ScheduleType.HOURLY;
    }

    @Override
    public Trigger buildTrigger(ScheduleDefinition definition) {
        if (definition.getCronExpression() != null && !definition.getCronExpression().isBlank()) {
            return new CronTrigger(definition.getCronExpression(), ZoneId.systemDefault());
        }
        int interval = definition.getIntervalHours() == null || definition.getIntervalHours() < 1
                ? 1
                : definition.getIntervalHours();
        return new CronTrigger("0 0 0/" + interval + " * * *", ZoneId.systemDefault());
    }

    @Override
    public ScheduleWindow createWindow(LocalDateTime triggerTime, ScheduleDefinition definition) {
        int interval = definition.getIntervalHours() == null || definition.getIntervalHours() < 1
                ? 1
                : definition.getIntervalHours();
        LocalDateTime end = triggerTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime start = end.minusHours(interval);
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.HOURLY)
                .triggerTime(triggerTime)
                .windowStart(start)
                .windowEnd(end)
                .fileToken("hourly_" + FILE_TOKEN.format(start))
                .build();
    }
}
