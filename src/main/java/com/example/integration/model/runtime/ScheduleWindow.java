package com.example.integration.model.runtime;

import com.example.integration.model.enums.ScheduleType;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Value
@Builder
public class ScheduleWindow {

    private static final DateTimeFormatter TIMESTAMP_TOKEN = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    ScheduleType scheduleType;
    LocalDateTime triggerTime;
    LocalDateTime windowStart;
    LocalDateTime windowEnd;
    String fileToken;

    public static ScheduleWindow adHoc(LocalDateTime now) {
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.AD_HOC)
                .triggerTime(now)
                .windowStart(now)
                .windowEnd(now)
                .fileToken("adhoc_" + TIMESTAMP_TOKEN.format(now))
                .build();
    }

    public static ScheduleWindow legacyCron(LocalDateTime now) {
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.LEGACY_CRON)
                .triggerTime(now)
                .windowStart(now)
                .windowEnd(now)
                .fileToken("legacy_" + TIMESTAMP_TOKEN.format(now))
                .build();
    }

    public static ScheduleWindow custom(
            LocalDateTime triggerTime,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            String fileToken) {
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.CUSTOM)
                .triggerTime(triggerTime)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .fileToken(StringUtils.hasText(fileToken)
                        ? fileToken
                        : "custom_" + TIMESTAMP_TOKEN.format(windowStart) + "_" + TIMESTAMP_TOKEN.format(windowEnd))
                .build();
    }
}
