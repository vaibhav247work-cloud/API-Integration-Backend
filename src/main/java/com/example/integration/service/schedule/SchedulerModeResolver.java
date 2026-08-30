package com.example.integration.service.schedule;

import com.example.integration.config.ScalableSchedulerProperties;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.enums.IntegrationSchedulerMode;
import com.example.integration.model.enums.MigrationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchedulerModeResolver {

    private final ScalableSchedulerProperties scalableSchedulerProperties;

    public boolean useQueueScheduling(IntegrationDefinition definition) {
        if (definition == null) {
            return scalableSchedulerProperties.getMigrationMode() == MigrationMode.QUEUE;
        }
        return switch (scalableSchedulerProperties.getMigrationMode()) {
            case LEGACY -> false;
            case QUEUE -> true;
            case DUAL -> definition.getSchedulerMode() == IntegrationSchedulerMode.QUEUE;
        };
    }
}
