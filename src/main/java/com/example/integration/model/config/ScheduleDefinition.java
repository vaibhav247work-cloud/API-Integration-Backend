package com.example.integration.model.config;

import com.example.integration.model.enums.ScheduleType;
import lombok.Data;

@Data
public class ScheduleDefinition {
    private ScheduleType type;
    private Boolean enabled = true;
    private Integer intervalHours = 1;
    private String cronExpression;
}
