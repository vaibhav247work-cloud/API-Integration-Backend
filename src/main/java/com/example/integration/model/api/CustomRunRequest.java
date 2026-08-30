package com.example.integration.model.api;

import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.runtime.ScheduleWindow;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomRunRequest(
        LocalDate fromDate,
        LocalDate toDate,
        LocalDateTime fromDateTime,
        LocalDateTime toDateTime,
        String fileToken) {

    public ScheduleWindow toScheduleWindow(LocalDateTime triggerTime) {
        boolean hasDateRange = fromDate != null || toDate != null;
        boolean hasDateTimeRange = fromDateTime != null || toDateTime != null;

        if (hasDateRange && hasDateTimeRange) {
            throw invalid("Use either fromDate/toDate or fromDateTime/toDateTime, not both");
        }
        if (!hasDateRange && !hasDateTimeRange) {
            throw invalid("Custom run requires fromDate/toDate or fromDateTime/toDateTime");
        }

        if (hasDateRange) {
            LocalDate effectiveFromDate = fromDate != null ? fromDate : toDate;
            LocalDate effectiveToDate = toDate != null ? toDate : effectiveFromDate;
            if (effectiveToDate.isBefore(effectiveFromDate)) {
                throw invalid("toDate must be on or after fromDate");
            }
            return ScheduleWindow.custom(
                    triggerTime,
                    effectiveFromDate.atStartOfDay(),
                    effectiveToDate.plusDays(1).atStartOfDay(),
                    normalizeFileToken());
        }

        if (fromDateTime == null || toDateTime == null) {
            throw invalid("Both fromDateTime and toDateTime are required for custom date-time runs");
        }
        if (!toDateTime.isAfter(fromDateTime)) {
            throw invalid("toDateTime must be after fromDateTime");
        }
        return ScheduleWindow.custom(triggerTime, fromDateTime, toDateTime, normalizeFileToken());
    }

    private String normalizeFileToken() {
        return StringUtils.hasText(fileToken) ? fileToken.trim() : null;
    }

    private IntegrationFailureException invalid(String message) {
        return new IntegrationFailureException(
                FailureCategory.CONFIGURATION_ERROR,
                message,
                "CUSTOM_RUN",
                null,
                null,
                null,
                false);
    }
}
