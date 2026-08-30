package com.example.integration.service;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.entity.FailedJobQueueItem;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.entity.IntegrationFieldMapping;
import com.example.integration.entity.IntegrationRun;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.config.PaginationConfig;
import com.example.integration.model.config.ResponseConfig;
import com.example.integration.model.config.StepConfig;
import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.RunStatus;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.model.runtime.RunDiagnostic;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StepResponse;
import com.example.integration.model.runtime.StoredArtifact;
import com.example.integration.repository.FailedJobQueueRepository;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.repository.IntegrationRunRepository;
import com.example.integration.service.execution.ExecutionJobMetrics;
import com.example.integration.service.execution.ExecutionJobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationOrchestrator {

    private final IntegrationDefinitionRepository integrationDefinitionRepository;
    private final IntegrationRunRepository integrationRunRepository;
    private final FailedJobQueueRepository failedJobQueueRepository;
    private final ConfigBindingService configBindingService;
    private final AuthService authService;
    private final PaginationService paginationService;
    private final ResponseExtractionService responseExtractionService;
    private final CsvService csvService;
    private final StorageService storageService;
    private final ExecutionJobStore executionJobStore;
    private final ExecutionJobMetrics executionJobMetrics;

    @Value("${integration.retry.delay-minutes:15}")
    private int retryDelayMinutes;

    @Async("integrationTaskExecutor")
    public CompletableFuture<Long> runAsync(Long integrationId) {
        return CompletableFuture.completedFuture(runInternal(
                integrationId,
                0,
                null,
                null,
                null,
                ScheduleWindow.adHoc(LocalDateTime.now())));
    }

    @Async("integrationTaskExecutor")
    public CompletableFuture<Long> runAsync(Long integrationId, ScheduleWindow scheduleWindow) {
        return CompletableFuture.completedFuture(runInternal(integrationId, 0, null, null, null, scheduleWindow));
    }

    @Async("integrationTaskExecutor")
    public CompletableFuture<Long> retryAsync(Long queueId) {
        FailedJobQueueItem queueItem = failedJobQueueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Retry queue item not found: " + queueId));

        if (!queueItem.isActive() || queueItem.getAttemptCount() >= queueItem.getMaxAttempts()) {
            return CompletableFuture.completedFuture(null);
        }
        ScheduleWindow scheduleWindow = resolveScheduleWindow(queueItem);
        return CompletableFuture.completedFuture(runInternal(
                queueItem.getIntegrationId(),
                queueItem.getAttemptCount() + 1,
                queueItem.getId(),
                null,
                null,
                scheduleWindow));
    }

    public Long runNow(Long integrationId) {
        return runInternal(integrationId, 0, null, null, null, ScheduleWindow.adHoc(LocalDateTime.now()));
    }

    public Long runNow(Long integrationId, ScheduleWindow scheduleWindow) {
        return runInternal(integrationId, 0, null, null, null, scheduleWindow);
    }

    public Long runQueuedJob(Long executionJobId, String workerId) {
        ExecutionJob executionJob = executionJobStore.findById(executionJobId)
                .orElseThrow(() -> new IllegalArgumentException("Execution job not found: " + executionJobId));

        return runInternal(
                executionJob.getIntegrationId(),
                executionJob.getAttemptCount(),
                null,
                executionJobId,
                workerId,
                ScheduleWindow.builder()
                        .scheduleType(executionJob.getScheduleType())
                        .triggerTime(executionJob.getPlannedFireAt())
                        .windowStart(executionJob.getWindowStart() == null ? executionJob.getPlannedFireAt() : executionJob.getWindowStart())
                        .windowEnd(executionJob.getWindowEnd() == null ? executionJob.getPlannedFireAt() : executionJob.getWindowEnd())
                        .fileToken(executionJob.getFileToken())
                        .build());
    }

    private Long runInternal(
            Long integrationId,
            int attemptNumber,
            Long queueId,
            Long executionJobId,
            String workerId,
            ScheduleWindow scheduleWindow) {
        IntegrationDefinition definition = integrationDefinitionRepository.findById(integrationId)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + integrationId));

        ExecutionContext context = new ExecutionContext(definition.getClientName(), definition.getBrandCode(), scheduleWindow);
        IntegrationRun run = startRun(definition, context, attemptNumber, scheduleWindow, executionJobId);
        Path csvFile = null;
        String currentStepName = null;
        String currentRequestUrl = null;
        Integer currentHttpStatusCode = null;
        String currentResponsePreview = null;

        try {
            MDC.put("correlationId", context.getCorrelationId());
            MDC.put("clientName", definition.getClientName());
            MDC.put("scheduleType", scheduleWindow.getScheduleType().name());
            log.info("Integration run started integrationId={} client={} scheduleType={} windowStart={} windowEnd={}",
                    definition.getId(),
                    definition.getClientName(),
                    scheduleWindow.getScheduleType(),
                    scheduleWindow.getWindowStart(),
                    scheduleWindow.getWindowEnd());

            List<IntegrationFieldMapping> mappings = definition.getFieldMappings().stream()
                    .sorted(Comparator.comparingInt(IntegrationFieldMapping::getSortOrder))
                    .toList();
            if (mappings.isEmpty()) {
                throw new IntegrationFailureException(
                        FailureCategory.CONFIGURATION_ERROR,
                        "Field mappings are required for integration " + definition.getId(),
                        "FIELD_MAPPING",
                        null,
                        null,
                        null,
                        false);
            }

            AuthConfig authConfig = configBindingService.getAuthConfig(definition);
            authService.apply(authConfig, context);

            PaginationConfig paginationConfig = configBindingService.getPaginationConfig(definition);
            ResponseConfig responseConfig = configBindingService.getResponseConfig(definition);
            List<StepConfig> steps = configBindingService.getStepConfigs(definition);
            if (steps.isEmpty()) {
                throw new IntegrationFailureException(
                        FailureCategory.CONFIGURATION_ERROR,
                        "No API steps configured for integration " + definition.getId(),
                        "STEP_CONFIG",
                        null,
                        null,
                        null,
                        false);
            }

            List<StepResponse> dataResponses = new ArrayList<>();
            StepResponse lastResponse = null;

            for (StepConfig step : steps) {
                if (Boolean.FALSE.equals(step.getEnabled())) {
                    continue;
                }
                currentStepName = step.getName();

                List<StepResponse> stepResponses = paginationService.execute(definition, step, paginationConfig, context);
                lastResponse = stepResponses.get(stepResponses.size() - 1);
                currentRequestUrl = lastResponse.getUrl();
                currentHttpStatusCode = lastResponse.getHttpStatusCode();
                currentResponsePreview = truncateResponsePreview(lastResponse.getBody());

                if (StringUtils.hasText(step.getResponseAlias())) {
                    context.putStepResponse(step.getResponseAlias(), lastResponse.getBody());
                }

                Map<String, String> variables = responseExtractionService.extractVariables(
                        lastResponse.getBody(),
                        lastResponse.getFormat(),
                        step.getResponseVariables(),
                        step.getResponseVariablePathType());
                variables.forEach(context::putVariable);

                if (Boolean.TRUE.equals(step.getDataStep())) {
                    dataResponses.addAll(stepResponses);
                }
            }

            if (dataResponses.isEmpty() && lastResponse != null) {
                dataResponses = List.of(lastResponse);
            }
            boolean emptyResponseReceived = dataResponses.stream()
                    .anyMatch(response -> !StringUtils.hasText(response.getBody()));

            List<java.util.LinkedHashMap<String, String>> records = responseExtractionService.extractRecords(
                    dataResponses,
                    responseConfig,
                    mappings,
                    context);

            if (records.isEmpty()) {
                run.setStatus(RunStatus.NO_DATA);
                run.setFailureCategory(emptyResponseReceived ? FailureCategory.EMPTY_RESPONSE : FailureCategory.NO_DATA);
                run.setFailedStepName(currentStepName);
                run.setFailedRequestUrl(currentRequestUrl);
                run.setHttpStatusCode(currentHttpStatusCode);
                run.setResponsePreview(currentResponsePreview);
                run.setErrorMessage(emptyResponseReceived
                        ? "API call completed but returned an empty response body"
                        : "API call completed but no records were extracted for the configured recordPath");
                LocalDateTime finishedAt = LocalDateTime.now();
                run.setFinishedAt(finishedAt);
                integrationRunRepository.save(run);
                closeQueueItem(queueId);
                completeQueuedNoData(executionJobId, workerId, run, finishedAt);
                log.warn("Integration returned no data integrationId={} client={} category={} step={} url={} status={}",
                        definition.getId(),
                        definition.getClientName(),
                        run.getFailureCategory(),
                        currentStepName,
                        currentRequestUrl,
                        currentHttpStatusCode);
                return run.getId();
            }

            csvFile = csvService.writeCsv(definition, mappings, records, context);
            StoredArtifact storedArtifact = storageService.store(definition, csvFile, scheduleWindow);

            run.setStatus(RunStatus.SUCCESS);
            run.setFailureCategory(null);
            run.setFailedStepName(null);
            run.setFailedRequestUrl(null);
            run.setHttpStatusCode(null);
            run.setResponsePreview(null);
            run.setOutputLocation(storedArtifact.location());
            run.setRecordsProcessed(records.size());
            LocalDateTime finishedAt = LocalDateTime.now();
            run.setFinishedAt(finishedAt);
            run.setErrorMessage(null);
            integrationRunRepository.save(run);

            closeQueueItem(queueId);
            completeQueuedSuccess(executionJobId, workerId, run, finishedAt);
            log.info("Integration run completed integrationId={} client={} runId={} recordsProcessed={} outputLocation={}",
                    definition.getId(), definition.getClientName(), run.getId(), records.size(), storedArtifact.location());
            return run.getId();
        } catch (Exception ex) {
            RunDiagnostic diagnostic = buildDiagnostic(ex, currentStepName, currentRequestUrl, currentHttpStatusCode, currentResponsePreview);
            log.error("Integration run failed integrationId={} client={} category={} step={} url={} status={} message={}",
                    definition.getId(),
                    definition.getClientName(),
                    diagnostic.getFailureCategory(),
                    diagnostic.getStepName(),
                    diagnostic.getRequestUrl(),
                    diagnostic.getHttpStatusCode(),
                    diagnostic.getMessage(),
                    ex);
            handleFailure(definition, run, attemptNumber, queueId, executionJobId, workerId, diagnostic);
            return run.getId();
        } finally {
            deleteQuietly(csvFile);
            MDC.clear();
        }
    }

    private IntegrationRun startRun(
            IntegrationDefinition definition,
            ExecutionContext context,
            int attemptNumber,
            ScheduleWindow scheduleWindow,
            Long executionJobId) {
        IntegrationRun run = new IntegrationRun();
        run.setIntegrationId(definition.getId());
        run.setClientName(definition.getClientName());
        run.setCorrelationId(context.getCorrelationId());
        run.setStatus(RunStatus.RUNNING);
        run.setAttemptNumber(attemptNumber);
        run.setScheduleType(scheduleWindow.getScheduleType());
        run.setWindowStart(scheduleWindow.getWindowStart());
        run.setWindowEnd(scheduleWindow.getWindowEnd());
        run.setFileToken(scheduleWindow.getFileToken());
        run.setExecutionJobId(executionJobId);
        run.setStartedAt(LocalDateTime.now());
        run.setRecordsProcessed(0);
        return integrationRunRepository.save(run);
    }

    private void handleFailure(
            IntegrationDefinition definition,
            IntegrationRun run,
            int attemptNumber,
            Long queueId,
            Long executionJobId,
            String workerId,
            RunDiagnostic diagnostic) {

        if (diagnostic.getFailureCategory() == FailureCategory.NO_DATA
                || diagnostic.getFailureCategory() == FailureCategory.EMPTY_RESPONSE) {
            LocalDateTime finishedAt = LocalDateTime.now();
            run.setFinishedAt(finishedAt);
            run.setFailureCategory(diagnostic.getFailureCategory());
            run.setFailedStepName(diagnostic.getStepName());
            run.setFailedRequestUrl(diagnostic.getRequestUrl());
            run.setHttpStatusCode(diagnostic.getHttpStatusCode());
            run.setResponsePreview(truncateResponsePreview(diagnostic.getResponsePreview()));
            run.setErrorMessage(truncate(diagnostic.getMessage()));
            run.setStatus(RunStatus.NO_DATA);
            integrationRunRepository.save(run);
            closeQueueItem(queueId);
            completeQueuedNoData(executionJobId, workerId, run, finishedAt);
            return;
        }

        boolean retryAvailable = diagnostic.isRetryable() && attemptNumber < definition.getMaxRetries();
        LocalDateTime finishedAt = LocalDateTime.now();
        run.setFinishedAt(finishedAt);
        run.setFailureCategory(diagnostic.getFailureCategory());
        run.setFailedStepName(diagnostic.getStepName());
        run.setFailedRequestUrl(diagnostic.getRequestUrl());
        run.setHttpStatusCode(diagnostic.getHttpStatusCode());
        run.setResponsePreview(truncateResponsePreview(diagnostic.getResponsePreview()));
        run.setErrorMessage(truncate(diagnostic.getMessage()));
        run.setStatus(retryAvailable ? RunStatus.RETRY_QUEUED : RunStatus.FAILED);
        integrationRunRepository.save(run);

        if (executionJobId != null) {
            if (retryAvailable) {
                executionJobStore.scheduleRetry(
                        executionJobId,
                        workerId,
                        attemptNumber + 1,
                        diagnostic.getFailureCategory(),
                        diagnostic.getStepName(),
                        diagnostic.getHttpStatusCode(),
                        truncate(diagnostic.getMessage()),
                        LocalDateTime.now().plusMinutes(retryDelayMinutes));
                executionJobMetrics.recordRetried();
                return;
            }

            ExecutionJobStatus terminalStatus = diagnostic.isRetryable()
                    ? ExecutionJobStatus.DEAD
                    : ExecutionJobStatus.FAILED;
            executionJobStore.markTerminalFailure(
                    executionJobId,
                    workerId,
                    terminalStatus,
                    diagnostic.getFailureCategory(),
                    diagnostic.getStepName(),
                    diagnostic.getHttpStatusCode(),
                    truncate(diagnostic.getMessage()),
                    finishedAt);
            executionJobMetrics.recordCompletion(terminalStatus, durationOf(run, finishedAt));
            return;
        }

        if (retryAvailable) {
            FailedJobQueueItem queueItem = queueId == null
                    ? new FailedJobQueueItem()
                    : failedJobQueueRepository.findById(queueId)
                    .orElseGet(FailedJobQueueItem::new);

            queueItem.setIntegrationId(definition.getId());
            queueItem.setRunId(run.getId());
            queueItem.setAttemptCount(attemptNumber);
            queueItem.setMaxAttempts(definition.getMaxRetries());
            queueItem.setFailureCategory(diagnostic.getFailureCategory());
            queueItem.setFailedStepName(diagnostic.getStepName());
            queueItem.setFailedRequestUrl(diagnostic.getRequestUrl());
            queueItem.setHttpStatusCode(diagnostic.getHttpStatusCode());
            queueItem.setLastError(truncate(diagnostic.getMessage()));
            queueItem.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            queueItem.setActive(true);
            failedJobQueueRepository.save(queueItem);
            return;
        }

        closeQueueItem(queueId);
    }

    private void completeQueuedSuccess(Long executionJobId, String workerId, IntegrationRun run, LocalDateTime finishedAt) {
        if (executionJobId == null) {
            return;
        }
        executionJobStore.markSuccess(executionJobId, workerId, finishedAt);
        executionJobMetrics.recordCompletion(ExecutionJobStatus.SUCCESS, durationOf(run, finishedAt));
    }

    private void completeQueuedNoData(Long executionJobId, String workerId, IntegrationRun run, LocalDateTime finishedAt) {
        if (executionJobId == null) {
            return;
        }
        executionJobStore.markNoData(
                executionJobId,
                workerId,
                run.getFailureCategory(),
                run.getErrorMessage(),
                finishedAt);
        executionJobMetrics.recordCompletion(ExecutionJobStatus.NO_DATA, durationOf(run, finishedAt));
    }

    private void closeQueueItem(Long queueId) {
        if (queueId == null) {
            return;
        }
        failedJobQueueRepository.findById(queueId).ifPresent(queueItem -> {
            queueItem.setActive(false);
            failedJobQueueRepository.save(queueItem);
        });
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= 3900 ? value : value.substring(0, 3900);
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
    }

    private ScheduleWindow resolveScheduleWindow(FailedJobQueueItem queueItem) {
        if (queueItem.getRunId() == null) {
            return ScheduleWindow.adHoc(LocalDateTime.now());
        }

        return integrationRunRepository.findById(queueItem.getRunId())
                .map(run -> ScheduleWindow.builder()
                        .scheduleType(run.getScheduleType())
                        .triggerTime(run.getStartedAt())
                        .windowStart(run.getWindowStart() == null ? run.getStartedAt() : run.getWindowStart())
                        .windowEnd(run.getWindowEnd() == null ? run.getStartedAt() : run.getWindowEnd())
                        .fileToken(run.getFileToken() == null ? "retry_" + run.getId() : run.getFileToken())
                        .build())
                .orElseGet(() -> ScheduleWindow.adHoc(LocalDateTime.now()));
    }

    private RunDiagnostic buildDiagnostic(
            Exception ex,
            String currentStepName,
            String currentRequestUrl,
            Integer currentHttpStatusCode,
            String currentResponsePreview) {
        if (ex instanceof IntegrationFailureException failureException) {
            if (shouldTreatParsingFailureAsNoData(failureException, currentHttpStatusCode, currentResponsePreview)) {
                return RunDiagnostic.builder()
                        .failureCategory(FailureCategory.NO_DATA)
                        .stepName(valueOrFallback(failureException.getStepName(), currentStepName))
                        .requestUrl(valueOrFallback(failureException.getRequestUrl(), currentRequestUrl))
                        .httpStatusCode(currentHttpStatusCode)
                        .responsePreview(valueOrFallback(failureException.getResponsePreview(), currentResponsePreview))
                        .message("API call completed but no data was available for the requested window")
                        .retryable(false)
                        .build();
            }
            return RunDiagnostic.builder()
                    .failureCategory(failureException.getFailureCategory())
                    .stepName(valueOrFallback(failureException.getStepName(), currentStepName))
                    .requestUrl(valueOrFallback(failureException.getRequestUrl(), currentRequestUrl))
                    .httpStatusCode(failureException.getHttpStatusCode() == null ? currentHttpStatusCode : failureException.getHttpStatusCode())
                    .responsePreview(valueOrFallback(failureException.getResponsePreview(), currentResponsePreview))
                    .message(failureException.getMessage())
                    .retryable(failureException.isRetryable())
                    .build();
        }

        FailureCategory category = ex instanceof IllegalArgumentException || ex instanceof IllegalStateException
                ? FailureCategory.CONFIGURATION_ERROR
                : FailureCategory.UNKNOWN_ERROR;
        return RunDiagnostic.builder()
                .failureCategory(category)
                .stepName(currentStepName)
                .requestUrl(currentRequestUrl)
                .httpStatusCode(currentHttpStatusCode)
                .responsePreview(currentResponsePreview)
                .message(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                .retryable(false)
                .build();
    }

    private String valueOrFallback(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private boolean shouldTreatParsingFailureAsNoData(
            IntegrationFailureException failureException,
            Integer currentHttpStatusCode,
            String currentResponsePreview) {
        return failureException.getFailureCategory() == FailureCategory.RESPONSE_PARSING_ERROR
                && currentHttpStatusCode != null
                && currentHttpStatusCode >= 300
                && currentHttpStatusCode < 400
                && !looksLikeStructuredPayload(currentResponsePreview);
    }

    private boolean looksLikeStructuredPayload(String responsePreview) {
        if (!StringUtils.hasText(responsePreview)) {
            return false;
        }
        String normalized = responsePreview.trim();
        return normalized.startsWith("<")
                || normalized.startsWith("{")
                || normalized.startsWith("[");
    }

    private String truncateResponsePreview(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        return body.length() <= 1000 ? body : body.substring(0, 1000);
    }

    private Duration durationOf(IntegrationRun run, LocalDateTime finishedAt) {
        if (run.getStartedAt() == null || finishedAt == null) {
            return null;
        }
        return Duration.between(run.getStartedAt(), finishedAt);
    }
}
