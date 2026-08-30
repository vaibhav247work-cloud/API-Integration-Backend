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
import com.example.integration.service.schedule.DailyScheduleStrategy;
import com.example.integration.service.schedule.HourlyScheduleStrategy;
import com.example.integration.service.schedule.MonthlyScheduleStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicScheduleServiceTest {

    @Mock
    private ThreadPoolTaskScheduler integrationTaskScheduler;

    @Mock
    private IntegrationDefinitionRepository integrationDefinitionRepository;

    @Mock
    private ConfigBindingService configBindingService;

    @Mock
    private IntegrationOrchestrator integrationOrchestrator;

    private DynamicScheduleService dynamicScheduleService;

    @BeforeEach
    void setUp() {
        dynamicScheduleService = new DynamicScheduleService(
                integrationTaskScheduler,
                integrationDefinitionRepository,
                configBindingService,
                integrationOrchestrator,
                List.of(
                        new HourlyScheduleStrategy(),
                        new DailyScheduleStrategy(),
                        new MonthlyScheduleStrategy()));
    }

    @Test
    void runScheduleNowBuildsWindowFromMatchingScheduleConfig() {
        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setId(15L);

        ScheduleDefinition daily = new ScheduleDefinition();
        daily.setType(ScheduleType.DAILY);

        ScheduleDefinition monthly = new ScheduleDefinition();
        monthly.setType(ScheduleType.MONTHLY);

        LocalDateTime triggerTime = LocalDateTime.of(2026, 4, 1, 1, 0);

        when(integrationDefinitionRepository.findById(15L)).thenReturn(Optional.of(definition));
        when(configBindingService.getScheduleDefinitions(definition)).thenReturn(List.of(daily, monthly));
        when(integrationOrchestrator.runNow(eq(15L), any(ScheduleWindow.class))).thenReturn(91L);

        Long runId = dynamicScheduleService.runScheduleNow(15L, ScheduleType.MONTHLY, triggerTime);

        ArgumentCaptor<ScheduleWindow> scheduleWindowCaptor = ArgumentCaptor.forClass(ScheduleWindow.class);
        verify(integrationOrchestrator).runNow(eq(15L), scheduleWindowCaptor.capture());

        ScheduleWindow scheduleWindow = scheduleWindowCaptor.getValue();
        assertThat(runId).isEqualTo(91L);
        assertThat(scheduleWindow.getScheduleType()).isEqualTo(ScheduleType.MONTHLY);
        assertThat(scheduleWindow.getTriggerTime()).isEqualTo(triggerTime);
        assertThat(scheduleWindow.getWindowStart()).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 0));
        assertThat(scheduleWindow.getWindowEnd()).isEqualTo(LocalDateTime.of(2026, 4, 1, 0, 0));
        assertThat(scheduleWindow.getFileToken()).isEqualTo("monthly_202603");
    }

    @Test
    void runScheduleNowFailsWhenRequestedScheduleTypeIsMissingOrDisabled() {
        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setId(15L);

        ScheduleDefinition monthly = new ScheduleDefinition();
        monthly.setType(ScheduleType.MONTHLY);
        monthly.setEnabled(false);

        when(integrationDefinitionRepository.findById(15L)).thenReturn(Optional.of(definition));
        when(configBindingService.getScheduleDefinitions(definition)).thenReturn(List.of(monthly));

        assertThatThrownBy(() -> dynamicScheduleService.runScheduleNow(
                15L,
                ScheduleType.MONTHLY,
                LocalDateTime.of(2026, 4, 1, 1, 0)))
                .isInstanceOf(IntegrationFailureException.class)
                .satisfies(ex -> {
                    IntegrationFailureException failure = (IntegrationFailureException) ex;
                    assertThat(failure.getFailureCategory()).isEqualTo(FailureCategory.CONFIGURATION_ERROR);
                    assertThat(failure.getMessage()).contains("MONTHLY");
                });

        verify(integrationOrchestrator, never()).runNow(anyLong(), any(ScheduleWindow.class));
    }
}
