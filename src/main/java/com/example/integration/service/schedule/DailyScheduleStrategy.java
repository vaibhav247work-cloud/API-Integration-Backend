package com.example.integration.service.schedule;

import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class DailyScheduleStrategy implements ScheduleStrategy {

    private static final String DEFAULT_CRON = "0 0 1 * * *";
    private static final DateTimeFormatter FILE_TOKEN = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public ScheduleType getType() {
        return ScheduleType.DAILY;
    }

    @Override
    public Trigger buildTrigger(ScheduleDefinition definition) {
        String cronExpression = definition.getCronExpression() == null || definition.getCronExpression().isBlank()
                ? DEFAULT_CRON
                : definition.getCronExpression();
        return new CronTrigger(cronExpression, ZoneId.systemDefault());
    }

    @Override
    public ScheduleWindow createWindow(LocalDateTime triggerTime, ScheduleDefinition definition) {
        LocalDate businessDate = triggerTime.toLocalDate().minusDays(1);
        LocalDateTime start = businessDate.atStartOfDay();
        LocalDateTime end = businessDate.plusDays(1).atStartOfDay();
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.DAILY)
                .triggerTime(triggerTime)
                .windowStart(start)
                .windowEnd(end)
                .fileToken("daily_" + FILE_TOKEN.format(businessDate))
                .build();
    }
}
