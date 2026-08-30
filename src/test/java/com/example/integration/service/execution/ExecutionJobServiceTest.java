package com.example.integration.service.execution;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.service.schedule.SchedulePlanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionJobServiceTest {

    @Mock
    private IntegrationDefinitionRepository integrationDefinitionRepository;

    @Mock
    private ExecutionJobStore executionJobStore;

    @Mock
    private ExecutionJobMetrics executionJobMetrics;

    @Mock
    private SchedulePlanningService schedulePlanningService;

    private ExecutionJobService executionJobService;

    @BeforeEach
    void setUp() {
        executionJobService = new ExecutionJobService(
                integrationDefinitionRepository,
                executionJobStore,
                executionJobMetrics,
                schedulePlanningService);
    }

    @Test
    void enqueueScheduledRunPersistsQueueJobWithWindowAndRetryLimits() {
        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setId(99L);
        definition.setMaxRetries(3);

        LocalDateTime plannedFireAt = LocalDateTime.of(2026, 3, 22, 19, 0);
        ScheduleWindow scheduleWindow = ScheduleWindow.builder()
                .scheduleType(ScheduleType.DAILY)
                .triggerTime(plannedFireAt)
                .windowStart(LocalDateTime.of(2026, 3, 21, 0, 0))
                .windowEnd(LocalDateTime.of(2026, 3, 22, 0, 0))
                .fileToken("daily_20260321")
                .build();

        when(integrationDefinitionRepository.findById(99L)).thenReturn(Optional.of(definition));
        when(schedulePlanningService.createWindow(definition, ScheduleType.DAILY, 0, plannedFireAt)).thenReturn(scheduleWindow);
        when(executionJobStore.enqueue(any(ExecutionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExecutionJob executionJob = executionJobService.enqueueScheduledRun(99L, ScheduleType.DAILY, 0, plannedFireAt);

        ArgumentCaptor<ExecutionJob> captor = ArgumentCaptor.forClass(ExecutionJob.class);
        verify(executionJobStore).enqueue(captor.capture());
        verify(executionJobMetrics).recordEnqueued();

        ExecutionJob saved = captor.getValue();
        assertThat(executionJob.getIntegrationId()).isEqualTo(99L);
        assertThat(saved.getScheduleType()).isEqualTo(ScheduleType.DAILY);
        assertThat(saved.getMaxAttempts()).isEqualTo(3);
        assertThat(saved.getPlannedFireAt()).isEqualTo(plannedFireAt);
        assertThat(saved.getWindowStart()).isEqualTo(scheduleWindow.getWindowStart());
        assertThat(saved.getWindowEnd()).isEqualTo(scheduleWindow.getWindowEnd());
        assertThat(saved.getDedupeKey()).contains("99", "DAILY", "daily_20260321");
    }
}
