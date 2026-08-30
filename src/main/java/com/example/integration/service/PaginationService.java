package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.config.PaginationConfig;
import com.example.integration.model.config.StepConfig;
import com.example.integration.model.enums.PaginationMode;
import com.example.integration.model.enums.RequestWindowMode;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StepResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaginationService {

    private final ApiClientService apiClientService;
    private final PathExtractorService pathExtractorService;

    public List<StepResponse> execute(
            IntegrationDefinition definition,
            StepConfig step,
            PaginationConfig paginationConfig,
            ExecutionContext context) {

        if (step.getRequestWindowMode() == RequestWindowMode.SINGLE_DATE) {
            return executeSingleDateWindow(definition, step, paginationConfig, context);
        }

        return executeStep(definition, step, paginationConfig, context);
    }

    private List<StepResponse> executeStep(
            IntegrationDefinition definition,
            StepConfig step,
            PaginationConfig paginationConfig,
            ExecutionContext context) {

        boolean shouldPaginate = paginationConfig != null
                && paginationConfig.isEnabled()
                && Boolean.TRUE.equals(step.getPaginate());

        if (!shouldPaginate) {
            return List.of(apiClientService.execute(definition, step, context));
        }

        if (paginationConfig.getMode() == PaginationMode.NEXT_URL) {
            return executeNextUrlPagination(definition, step, paginationConfig, context);
        }
        return executePageNumberPagination(definition, step, paginationConfig, context);
    }

    private List<StepResponse> executeSingleDateWindow(
            IntegrationDefinition definition,
            StepConfig step,
            PaginationConfig paginationConfig,
            ExecutionContext context) {

        List<LocalDate> requestDates = resolveRequestDates(context.getScheduleWindow());
        String requestDateVariable = StringUtils.hasText(step.getRequestDateVariable())
                ? step.getRequestDateVariable()
                : "requestDate";
        DateTimeFormatter formatter = resolveFormatter(step.getRequestDateFormat());
        Map<String, String> originalValues = captureVariables(context, requestDateVariable);
        List<StepResponse> responses = new ArrayList<>();

        log.info("Executing single-date window fan-out client={} step={} dates={}",
                definition.getClientName(), step.getName(), requestDates.size());

        try {
            for (LocalDate requestDate : requestDates) {
                String formattedDate = formatter.format(requestDate);
                context.putVariable("requestDate", formattedDate);
                context.putVariable("requestDateIso", requestDate.toString());
                context.putVariable("processDate", requestDate.toString());
                context.putVariable(requestDateVariable, formattedDate);
                responses.addAll(executeStep(definition, step, paginationConfig, context));
            }
            return responses;
        } finally {
            restoreVariables(context, originalValues);
        }
    }

    private List<StepResponse> executePageNumberPagination(
            IntegrationDefinition definition,
            StepConfig step,
            PaginationConfig paginationConfig,
            ExecutionContext context) {

        if (!StringUtils.hasText(paginationConfig.getTotalPagesPath())) {
            throw new IllegalStateException("PAGE_NUMBER pagination requires totalPagesPath");
        }

        List<StepResponse> responses = new ArrayList<>();
        int page = paginationConfig.getStartPage() == null ? 1 : paginationConfig.getStartPage();
        Integer totalPages = null;

        while (totalPages == null || page <= totalPages) {
            context.putVariable("page", page);
            context.putVariable(paginationConfig.getPageParam(), page);
            if (StringUtils.hasText(paginationConfig.getSizeParam()) && paginationConfig.getPageSize() != null) {
                context.putVariable(paginationConfig.getSizeParam(), paginationConfig.getPageSize());
            }

            StepResponse response = apiClientService.execute(definition, step, context);
            responses.add(response);

            if (totalPages == null) {
                totalPages = pathExtractorService.extractInteger(
                        response.getBody(),
                        response.getFormat(),
                        paginationConfig.getTotalPagesPath(),
                        paginationConfig.getTotalPagesPathType());
                if (totalPages == null) {
                    break;
                }
            }
            page++;
        }

        return responses;
    }

    private List<StepResponse> executeNextUrlPagination(
            IntegrationDefinition definition,
            StepConfig step,
            PaginationConfig paginationConfig,
            ExecutionContext context) {

        List<StepResponse> responses = new ArrayList<>();
        String nextUrl = null;
        boolean firstIteration = true;

        while (firstIteration || StringUtils.hasText(nextUrl)) {
            StepConfig currentStep = copy(step);
            if (!firstIteration) {
                currentStep.setUrl(nextUrl);
            }

            StepResponse response = apiClientService.execute(definition, currentStep, context);
            responses.add(response);
            nextUrl = pathExtractorService.extractString(
                    response.getBody(),
                    response.getFormat(),
                    paginationConfig.getNextPagePath(),
                    paginationConfig.getNextPagePathType());
            firstIteration = false;
        }
        return responses;
    }

    private StepConfig copy(StepConfig source) {
        StepConfig copy = new StepConfig();
        copy.setOrderIndex(source.getOrderIndex());
        copy.setName(source.getName());
        copy.setEnabled(source.getEnabled());
        copy.setMethod(source.getMethod());
        copy.setUrl(source.getUrl());
        copy.setHeaders(source.getHeaders());
        copy.setQueryParams(source.getQueryParams());
        copy.setBodyTemplate(source.getBodyTemplate());
        copy.setRequestFormat(source.getRequestFormat());
        copy.setResponseFormat(source.getResponseFormat());
        copy.setRequestWindowMode(source.getRequestWindowMode());
        copy.setRequestDateVariable(source.getRequestDateVariable());
        copy.setRequestDateFormat(source.getRequestDateFormat());
        copy.setPaginate(source.getPaginate());
        copy.setDataStep(source.getDataStep());
        copy.setResponseAlias(source.getResponseAlias());
        copy.setResponseVariables(source.getResponseVariables());
        copy.setResponseVariablePathType(source.getResponseVariablePathType());
        return copy;
    }

    private List<LocalDate> resolveRequestDates(ScheduleWindow scheduleWindow) {
        if (scheduleWindow == null || scheduleWindow.getWindowStart() == null) {
            return List.of(LocalDate.now());
        }

        LocalDate startDate = scheduleWindow.getWindowStart().toLocalDate();
        LocalDate endDateExclusive = scheduleWindow.getWindowEnd() == null
                ? startDate
                : scheduleWindow.getWindowEnd().toLocalDate();

        if (!endDateExclusive.isAfter(startDate)) {
            return List.of(startDate);
        }

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate current = startDate; current.isBefore(endDateExclusive); current = current.plusDays(1)) {
            dates.add(current);
        }
        return dates;
    }

    private DateTimeFormatter resolveFormatter(String pattern) {
        return StringUtils.hasText(pattern)
                ? DateTimeFormatter.ofPattern(pattern)
                : DateTimeFormatter.ISO_LOCAL_DATE;
    }

    private Map<String, String> captureVariables(ExecutionContext context, String requestDateVariable) {
        Map<String, String> originalValues = new LinkedHashMap<>();
        originalValues.put("requestDate", context.getVariableAsString("requestDate"));
        originalValues.put("requestDateIso", context.getVariableAsString("requestDateIso"));
        originalValues.put("processDate", context.getVariableAsString("processDate"));
        if (!"requestDate".equals(requestDateVariable)) {
            originalValues.put(requestDateVariable, context.getVariableAsString(requestDateVariable));
        }
        return originalValues;
    }

    private void restoreVariables(ExecutionContext context, Map<String, String> originalValues) {
        originalValues.forEach((key, value) -> {
            if (value == null) {
                context.getVariables().remove(key);
            } else {
                context.putVariable(key, value);
            }
        });
    }
}
