package com.example.integration.service.schedule;

import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class MonthlyScheduleStrategy implements ScheduleStrategy {

    private static final String DEFAULT_CRON = "0 0 1 1 * *";
    private static final DateTimeFormatter FILE_TOKEN = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    public ScheduleType getType() {
        return ScheduleType.MONTHLY;
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
        YearMonth previousMonth = YearMonth.from(triggerTime.minusMonths(1));
        LocalDateTime start = previousMonth.atDay(1).atStartOfDay();
        LocalDateTime end = previousMonth.plusMonths(1).atDay(1).atStartOfDay();
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.MONTHLY)
                .triggerTime(triggerTime)
                .windowStart(start)
                .windowEnd(end)
                .fileToken("monthly_" + FILE_TOKEN.format(previousMonth.atDay(1)))
                .build();
    }
}
