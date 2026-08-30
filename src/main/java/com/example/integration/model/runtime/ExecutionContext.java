package com.example.integration.model.runtime;

import lombok.Getter;
import com.example.integration.model.enums.ScheduleType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class ExecutionContext {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final String correlationId = UUID.randomUUID().toString();
    private final ScheduleWindow scheduleWindow;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<String, String> authHeaders = new ConcurrentHashMap<>();
    private final Map<String, String> authQueryParams = new ConcurrentHashMap<>();
    private final Map<String, String> stepResponses = new ConcurrentHashMap<>();

    public ExecutionContext(String clientName, String brandCode, ScheduleWindow scheduleWindow) {
        this.scheduleWindow = scheduleWindow;
        variables.put("clientName", clientName);
        variables.put("brandCode", brandCode);
        variables.put("correlationId", correlationId);
        variables.put("today", LocalDate.now().toString());
        variables.put("now", LocalDateTime.now().toString());
        addScheduleVariables(scheduleWindow);
    }

    public void putVariable(String key, Object value) {
        if (key != null && value != null) {
            variables.put(key, value);
        }
    }

    public String getVariableAsString(String key) {
        Object value = variables.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public void putAuthHeader(String key, String value) {
        if (key != null && value != null) {
            authHeaders.put(key, value);
        }
    }

    public void putAuthQueryParam(String key, String value) {
        if (key != null && value != null) {
            authQueryParams.put(key, value);
        }
    }

    public void putStepResponse(String key, String responseBody) {
        if (key != null && responseBody != null) {
            stepResponses.put(key, responseBody);
            variables.put(key + "Response", responseBody);
        }
    }

    private void addScheduleVariables(ScheduleWindow scheduleWindow) {
        if (scheduleWindow == null) {
            return;
        }
        variables.put("scheduleType", scheduleWindow.getScheduleType().name());
        variables.put("fileToken", scheduleWindow.getFileToken());
        variables.put("triggerDateTime", DATE_TIME_FORMATTER.format(scheduleWindow.getTriggerTime()));
        variables.put("windowStartDateTime", DATE_TIME_FORMATTER.format(scheduleWindow.getWindowStart()));
        variables.put("windowEndDateTime", DATE_TIME_FORMATTER.format(scheduleWindow.getWindowEnd()));
        variables.put("windowStartDate", scheduleWindow.getWindowStart().toLocalDate().toString());
        variables.put("windowEndDateExclusive", scheduleWindow.getWindowEnd().toLocalDate().toString());
        variables.put("processDate", scheduleWindow.getWindowStart().toLocalDate().toString());
        variables.put("processMonth", MONTH_FORMATTER.format(scheduleWindow.getWindowStart()));
        variables.put("processHour", HOUR_FORMATTER.format(scheduleWindow.getWindowStart()));

        if (scheduleWindow.getScheduleType() == ScheduleType.DAILY) {
            variables.put("businessDate", scheduleWindow.getWindowStart().toLocalDate().toString());
        }
        if (scheduleWindow.getScheduleType() == ScheduleType.MONTHLY) {
            variables.put("previousMonth", YearMonth.from(scheduleWindow.getWindowStart()).toString());
        }
    }
}
