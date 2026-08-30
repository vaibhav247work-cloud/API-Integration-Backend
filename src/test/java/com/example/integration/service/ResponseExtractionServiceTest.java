package com.example.integration.service;

import com.example.integration.entity.IntegrationFieldMapping;
import com.example.integration.model.config.ResponseConfig;
import com.example.integration.model.enums.MappingType;
import com.example.integration.model.enums.PathType;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.runtime.StepResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseExtractionServiceTest {

    private final ResponseExtractionService service = new ResponseExtractionService(
            null,
            new MappingExpressionService(),
            new DuplicateResolutionService());

    @Test
    void shouldFormatDateFieldUsingAutoDetectedInput() {
        IntegrationFieldMapping mapping = mapping("$.billDate", "BILL_DATE", "DATE:dd-MM-yyyy");
        StepResponse response = StepResponse.builder()
                .body("""
                        {"data":[{"billDate":"2026-03-14"}]}
                        """)
                .format(PayloadFormat.JSON)
                .build();

        List<LinkedHashMap<String, String>> rows = service.extractRecords(
                List.of(response),
                responseConfig("$.data"),
                List.of(mapping),
                null);

        assertEquals("14-03-2026", rows.get(0).get("BILL_DATE"));
    }

    @Test
    void shouldFormatDateTimeFieldUsingAutoDetectedInput() {
        IntegrationFieldMapping mapping = mapping("$.billDateTime", "BILL_DATE_TIME", "DATETIME:dd/MM/yyyy HH:mm:ss");
        StepResponse response = StepResponse.builder()
                .body("""
                        {"data":[{"billDateTime":"2026-03-14T18:45:30"}]}
                        """)
                .format(PayloadFormat.JSON)
                .build();

        List<LinkedHashMap<String, String>> rows = service.extractRecords(
                List.of(response),
                responseConfig("$.data"),
                List.of(mapping),
                null);

        assertEquals("14/03/2026 18:45:30", rows.get(0).get("BILL_DATE_TIME"));
    }

    @Test
    void shouldFormatDateTimeFieldUsingCustomInputAndOutputPatterns() {
        IntegrationFieldMapping mapping = mapping(
                "$.billDateTime",
                "BILL_DATE_TIME",
                "DATETIME:yyyyMMddHHmmss->dd-MM-yyyy HH:mm:ss");
        StepResponse response = StepResponse.builder()
                .body("""
                        {"data":[{"billDateTime":"20260314184530"}]}
                        """)
                .format(PayloadFormat.JSON)
                .build();

        List<LinkedHashMap<String, String>> rows = service.extractRecords(
                List.of(response),
                responseConfig("$.data"),
                List.of(mapping),
                null);

        assertEquals("14-03-2026 18:45:30", rows.get(0).get("BILL_DATE_TIME"));
    }

    private IntegrationFieldMapping mapping(String sourcePath, String targetHeader, String formatter) {
        IntegrationFieldMapping mapping = new IntegrationFieldMapping();
        mapping.setMappingType(MappingType.SOURCE_PATH);
        mapping.setPathType(PathType.JSON_PATH);
        mapping.setSourcePath(sourcePath);
        mapping.setTargetHeader(targetHeader);
        mapping.setFormatter(formatter);
        return mapping;
    }

    private ResponseConfig responseConfig(String recordPath) {
        ResponseConfig config = new ResponseConfig();
        config.setRecordPath(recordPath);
        config.setRecordPathType(PathType.JSON_PATH);
        return config;
    }
}
