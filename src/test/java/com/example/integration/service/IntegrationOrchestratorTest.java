package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.entity.IntegrationFieldMapping;
import com.example.integration.entity.IntegrationRun;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.PaginationConfig;
import com.example.integration.model.config.ResponseConfig;
import com.example.integration.model.config.StepConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.MappingType;
import com.example.integration.model.enums.PathType;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.enums.RunStatus;
import com.example.integration.model.runtime.StepResponse;
import com.example.integration.repository.FailedJobQueueRepository;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.repository.IntegrationRunRepository;
import com.example.integration.service.execution.ExecutionJobMetrics;
import com.example.integration.service.execution.ExecutionJobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationOrchestratorTest {

    @Mock
    private IntegrationDefinitionRepository integrationDefinitionRepository;

    @Mock
    private IntegrationRunRepository integrationRunRepository;

    @Mock
    private FailedJobQueueRepository failedJobQueueRepository;

    @Mock
    private ConfigBindingService configBindingService;

    @Mock
    private AuthService authService;

    @Mock
    private PaginationService paginationService;

    @Mock
    private ResponseExtractionService responseExtractionService;

    @Mock
    private CsvService csvService;

    @Mock
    private StorageService storageService;

    @Mock
    private ExecutionJobStore executionJobStore;

    @Mock
    private ExecutionJobMetrics executionJobMetrics;

    @InjectMocks
    private IntegrationOrchestrator integrationOrchestrator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(integrationOrchestrator, "retryDelayMinutes", 15);
    }

    @Test
    void runNowMarksRedirectLikeXmlNoDataAsNoData() {
        IntegrationDefinition definition = buildDefinition();
        StepConfig step = buildStep();
        StepResponse response = StepResponse.builder()
                .stepName("fetch-adsr-sales")
                .url("https://login.olabi.ooo/adsr-aws-s3/Alpha-one-Ahmedabad/100827/18-03-2026")
                .body("pathfinder/Alpha-one-Ahmedabad/CKJ-FSS-Alpha-One-AH-100827/03-2026/100827-18-03-2026.xml")
                .format(PayloadFormat.XML)
                .httpStatusCode(302)
                .build();

        stubCommon(definition, step, response);
        when(responseExtractionService.extractRecords(anyList(), any(ResponseConfig.class), anyList(), any()))
                .thenThrow(new IntegrationFailureException(
                        FailureCategory.RESPONSE_PARSING_ERROR,
                        "Failed to parse XML response",
                        "RESPONSE_EXTRACTION",
                        null,
                        null,
                        response.getBody(),
                        false));

        Long runId = integrationOrchestrator.runNow(definition.getId());
        IntegrationRun finalRun = captureFinalRun();

        assertThat(runId).isEqualTo(finalRun.getId());
        assertThat(finalRun.getStatus()).isEqualTo(RunStatus.NO_DATA);
        assertThat(finalRun.getFailureCategory()).isEqualTo(FailureCategory.NO_DATA);
        assertThat(finalRun.getHttpStatusCode()).isEqualTo(302);
        assertThat(finalRun.getErrorMessage()).contains("no data was available");
        verify(failedJobQueueRepository, never()).save(any());
    }

    @Test
    void runNowKeepsRealXmlParsingFailuresAsFailed() {
        IntegrationDefinition definition = buildDefinition();
        StepConfig step = buildStep();
        StepResponse response = StepResponse.builder()
                .stepName("fetch-adsr-sales")
                .url("https://login.olabi.ooo/adsr-aws-s3/Alpha-one-Ahmedabad/100827/18-03-2026")
                .body("not xml")
                .format(PayloadFormat.XML)
                .httpStatusCode(200)
                .build();

        stubCommon(definition, step, response);
        when(responseExtractionService.extractRecords(anyList(), any(ResponseConfig.class), anyList(), any()))
                .thenThrow(new IntegrationFailureException(
                        FailureCategory.RESPONSE_PARSING_ERROR,
                        "Failed to parse XML response",
                        "RESPONSE_EXTRACTION",
                        null,
                        null,
                        response.getBody(),
                        false));

        Long runId = integrationOrchestrator.runNow(definition.getId());
        IntegrationRun finalRun = captureFinalRun();

        assertThat(runId).isEqualTo(finalRun.getId());
        assertThat(finalRun.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(finalRun.getFailureCategory()).isEqualTo(FailureCategory.RESPONSE_PARSING_ERROR);
        assertThat(finalRun.getHttpStatusCode()).isEqualTo(200);
        verify(failedJobQueueRepository, never()).save(any());
    }

    private void stubCommon(IntegrationDefinition definition, StepConfig step, StepResponse response) {
        AtomicLong runIdSequence = new AtomicLong(100);
        when(integrationDefinitionRepository.findById(definition.getId())).thenReturn(Optional.of(definition));
        when(integrationRunRepository.save(any(IntegrationRun.class))).thenAnswer(invocation -> {
            IntegrationRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(runIdSequence.getAndIncrement());
            }
            return run;
        });
        when(configBindingService.getPaginationConfig(definition)).thenReturn(new PaginationConfig());
        when(configBindingService.getResponseConfig(definition)).thenReturn(new ResponseConfig());
        when(configBindingService.getStepConfigs(definition)).thenReturn(List.of(step));
        when(paginationService.execute(any(IntegrationDefinition.class), any(StepConfig.class), any(PaginationConfig.class), any()))
                .thenReturn(List.of(response));
        when(responseExtractionService.extractVariables(any(), any(), any(), any())).thenReturn(Map.of());
    }

    private IntegrationRun captureFinalRun() {
        ArgumentCaptor<IntegrationRun> captor = ArgumentCaptor.forClass(IntegrationRun.class);
        verify(integrationRunRepository, atLeast(2)).save(captor.capture());
        List<IntegrationRun> savedRuns = captor.getAllValues();
        return savedRuns.get(savedRuns.size() - 1);
    }

    private IntegrationDefinition buildDefinition() {
        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setId(12L);
        definition.setClientName("Olabi ADSR XML Sales");
        definition.setBrandCode("OLABI");
        definition.setMaxRetries(0);
        definition.addMapping(buildMapping());
        return definition;
    }

    private IntegrationFieldMapping buildMapping() {
        IntegrationFieldMapping mapping = new IntegrationFieldMapping();
        mapping.setSortOrder(1);
        mapping.setMappingType(MappingType.SOURCE_PATH);
        mapping.setSourcePath("./*[local-name()='RECEIPT_NO']");
        mapping.setPathType(PathType.XPATH);
        mapping.setTargetHeader("Invoice_No");
        mapping.setRequiredFlag(true);
        return mapping;
    }

    private StepConfig buildStep() {
        StepConfig step = new StepConfig();
        step.setName("fetch-adsr-sales");
        step.setDataStep(true);
        step.setEnabled(true);
        step.setResponseFormat(PayloadFormat.XML);
        return step;
    }
}
