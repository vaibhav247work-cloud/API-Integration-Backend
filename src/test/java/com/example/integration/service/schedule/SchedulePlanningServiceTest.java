package com.example.integration.service.schedule;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.service.ConfigBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulePlanningServiceTest {

    @Mock
    private ConfigBindingService configBindingService;

    private SchedulePlanningService schedulePlanningService;

    @BeforeEach
    void setUp() {
        schedulePlanningService = new SchedulePlanningService(
                configBindingService,
                List.of(
                        new HourlyScheduleStrategy(),
                        new DailyScheduleStrategy(),
                        new MonthlyScheduleStrategy()));
    }

    @Test
    void resolveEnabledSchedulesBuildsStableDistributedHourlyCron() {
        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setId(42L);

        ScheduleDefinition hourly = new ScheduleDefinition();
        hourly.setType(ScheduleType.HOURLY);
        hourly.setIntervalHours(2);

        when(configBindingService.getScheduleDefinitions(definition)).thenReturn(List.of(hourly));

        List<SchedulePlanningService.ResolvedSchedulePlan> first = schedulePlanningService.resolveEnabledSchedules(definition);
        List<SchedulePlanningService.ResolvedSchedulePlan> second = schedulePlanningService.resolveEnabledSchedules(definition);

        assertThat(first).hasSize(1);
        assertThat(first.get(0).cronExpression()).isEqualTo(second.get(0).cronExpression());
        assertThat(first.get(0).cronExpression()).matches("0 \\d{1,2} 0/2 \\* \\* \\?");
    }

    @Test
    void resolveEnabledSchedulesNormalizesLegacySpringCronForQuartz() {
        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setId(7L);
        definition.setScheduleCron("0 8 6 * * *");

        when(configBindingService.getScheduleDefinitions(definition)).thenReturn(List.of());

        List<SchedulePlanningService.ResolvedSchedulePlan> plans = schedulePlanningService.resolveEnabledSchedules(definition);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).cronExpression()).isEqualTo("0 8 6 * * ?");
    }
}
