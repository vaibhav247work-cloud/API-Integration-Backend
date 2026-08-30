package com.example.integration.service;

import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.DuplicateHandlingConfig;
import com.example.integration.model.config.ResponseConfig;
import com.example.integration.model.enums.DuplicateFieldAction;
import com.example.integration.model.enums.FailureCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class DuplicateResolutionService {

    private static final String KEY_SEPARATOR = "\u001F";

    public List<LinkedHashMap<String, String>> resolve(
            List<LinkedHashMap<String, String>> records,
            ResponseConfig responseConfig) {

        if (records == null || records.isEmpty() || responseConfig == null) {
            return records;
        }

        DuplicateHandlingConfig config = responseConfig.getDuplicateHandling();
        if (config == null || !config.isEnabled()) {
            return records;
        }

        if (config.getKeyHeaders() == null || config.getKeyHeaders().isEmpty()) {
            throw new IntegrationFailureException(
                    FailureCategory.CONFIGURATION_ERROR,
                    "duplicateHandling.keyHeaders is required when duplicate handling is enabled",
                    "DUPLICATE_HANDLING",
                    null,
                    null,
                    null,
                    false);
        }

        validateConfiguredHeaders(records.get(0), config);

        LinkedHashMap<String, LinkedHashMap<String, String>> groupedRecords = new LinkedHashMap<>();
        AtomicInteger syntheticIndex = new AtomicInteger();

        for (LinkedHashMap<String, String> record : records) {
            String key = buildGroupingKey(record, config, syntheticIndex.getAndIncrement());
            LinkedHashMap<String, String> existing = groupedRecords.get(key);
            if (existing == null) {
                groupedRecords.put(key, new LinkedHashMap<>(record));
                continue;
            }
            merge(existing, record, config);
        }

        int removedCount = records.size() - groupedRecords.size();
        if (removedCount > 0) {
            log.info("Duplicate resolution applied originalRecords={} finalRecords={} mergedRecords={}",
                    records.size(), groupedRecords.size(), removedCount);
        }
        return new ArrayList<>(groupedRecords.values());
    }

    private void validateConfiguredHeaders(
            LinkedHashMap<String, String> sampleRecord,
            DuplicateHandlingConfig config) {

        for (String keyHeader : config.getKeyHeaders()) {
            if (!sampleRecord.containsKey(keyHeader)) {
                throw invalidHeader("Duplicate key header not found in mapped output: " + keyHeader);
            }
        }

        if (config.getFieldActions() == null) {
            return;
        }
        for (String header : config.getFieldActions().keySet()) {
            if (!sampleRecord.containsKey(header)) {
                throw invalidHeader("Duplicate action header not found in mapped output: " + header);
            }
        }
    }

    private String buildGroupingKey(
            Map<String, String> record,
            DuplicateHandlingConfig config,
            int syntheticIndex) {

        List<String> parts = new ArrayList<>();
        for (String keyHeader : config.getKeyHeaders()) {
            String value = record.get(keyHeader);
            if (!StringUtils.hasText(value)) {
                return "__unique__" + syntheticIndex;
            }
            parts.add(value.trim());
        }
        return String.join(KEY_SEPARATOR, parts);
    }

    private void merge(
            LinkedHashMap<String, String> target,
            LinkedHashMap<String, String> incoming,
            DuplicateHandlingConfig config) {

        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String header = entry.getKey();
            if (config.getKeyHeaders().contains(header)) {
                continue;
            }

            DuplicateFieldAction action = resolveAction(header, config);
            if (action == DuplicateFieldAction.SUM) {
                target.put(header, sum(header, target.get(header), entry.getValue()));
            }
        }
    }

    private DuplicateFieldAction resolveAction(String header, DuplicateHandlingConfig config) {
        if (config.getFieldActions() == null || config.getFieldActions().isEmpty()) {
            return config.getDefaultAction() == null
                    ? DuplicateFieldAction.KEEP_FIRST
                    : config.getDefaultAction();
        }
        return config.getFieldActions().getOrDefault(
                header,
                config.getDefaultAction() == null ? DuplicateFieldAction.KEEP_FIRST : config.getDefaultAction());
    }

    private String sum(String header, String left, String right) {
        try {
            BigDecimal leftValue = toBigDecimal(left);
            BigDecimal rightValue = toBigDecimal(right);
            return leftValue.add(rightValue).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            throw new IntegrationFailureException(
                    FailureCategory.MAPPING_ERROR,
                    "Duplicate SUM requires numeric values for header " + header,
                    "DUPLICATE_HANDLING",
                    null,
                    null,
                    truncate(left, right),
                    false,
                    ex);
        }
    }

    private BigDecimal toBigDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private IntegrationFailureException invalidHeader(String message) {
        return new IntegrationFailureException(
                FailureCategory.CONFIGURATION_ERROR,
                message,
                "DUPLICATE_HANDLING",
                null,
                null,
                null,
                false);
    }

    private String truncate(String left, String right) {
        String combined = "left=" + left + ", right=" + right;
        return combined.length() <= 1000 ? combined : combined.substring(0, 1000);
    }
}
