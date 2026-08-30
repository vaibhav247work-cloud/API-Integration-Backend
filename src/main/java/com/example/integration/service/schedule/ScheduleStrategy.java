package com.example.integration.service.schedule;

import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import org.springframework.scheduling.Trigger;

import java.time.LocalDateTime;

public interface ScheduleStrategy {
    ScheduleType getType();

    Trigger buildTrigger(ScheduleDefinition definition);

    ScheduleWindow createWindow(LocalDateTime triggerTime, ScheduleDefinition definition);
}
