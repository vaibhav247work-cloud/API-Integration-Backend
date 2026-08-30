package com.example.integration.service;

import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.entity.IntegrationFieldMapping;
import com.example.integration.model.enums.MappingType;
import com.example.integration.model.config.ResponseConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.PathType;
import com.example.integration.model.enums.PayloadFormat;
import com.example.integration.model.runtime.ExecutionContext;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StepResponse;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResponseExtractionService {

    private final PathExtractorService pathExtractorService;
    private final MappingExpressionService mappingExpressionService;
    private final DuplicateResolutionService duplicateResolutionService;

    public List<LinkedHashMap<String, String>> extractRecords(
            List<StepResponse> responses,
            ResponseConfig responseConfig,
            List<IntegrationFieldMapping> mappings,
            ExecutionContext context) {

        List<LinkedHashMap<String, String>> records = new ArrayList<>();
        for (StepResponse response : responses) {
            if (!StringUtils.hasText(response.getBody())) {
                continue;
            }
            if (response.getFormat() == PayloadFormat.JSON) {
                records.addAll(extractJsonRecords(response.getBody(), responseConfig, mappings, context));
            } else {
                records.addAll(extractXmlRecords(response.getBody(), responseConfig, mappings, context));
            }
        }
        return duplicateResolutionService.resolve(records, responseConfig);
    }

    public Map<String, String> extractVariables(
            String body,
            PayloadFormat format,
            Map<String, String> variablePathMap,
            PathType pathType) {

        Map<String, String> variables = new LinkedHashMap<>();
        if (variablePathMap == null || variablePathMap.isEmpty()) {
            return variables;
        }
        if (!StringUtils.hasText(body)) {
            return variables;
        }

        variablePathMap.forEach((path, variableName) -> {
            String value = pathExtractorService.extractString(body, format, path, pathType);
            if (value != null) {
                variables.put(variableName, value);
            }
        });
        return variables;
    }

    private List<LinkedHashMap<String, String>> extractJsonRecords(
            String body,
            ResponseConfig responseConfig,
            List<IntegrationFieldMapping> mappings,
            ExecutionContext context) {
        try {
            Object recordRoot = StringUtils.hasText(recordPath(responseConfig))
                    ? JsonPath.read(body, recordPath(responseConfig))
                    : JsonPath.parse(body).json();

            List<?> sourceRecords = recordRoot instanceof List<?> list ? list : List.of(recordRoot);
            List<LinkedHashMap<String, String>> records = new ArrayList<>();
            for (Object sourceRecord : sourceRecords) {
                MappingExpressionService.RecordAccessor recordAccessor = new JsonRecordAccessor(sourceRecord);
                if (!matchesWindow(responseConfig, recordAccessor, context)) {
                    continue;
                }
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                for (IntegrationFieldMapping mapping : mappings) {
                    String value = resolveMappingValue(mapping, recordAccessor, row, context);
                    row.put(mapping.getTargetHeader(), applyFormatter(resolveValue(mapping, value), mapping.getFormatter()));
                }
                records.add(row);
            }
            return records;
        } catch (IntegrationFailureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IntegrationFailureException(
                    FailureCategory.RESPONSE_PARSING_ERROR,
                    "Failed to extract JSON records for path [" + recordPath(responseConfig) + "]",
                    "RESPONSE_EXTRACTION",
                    null,
                    null,
                    truncate(body),
                    false,
                    ex);
        }
    }

    private List<LinkedHashMap<String, String>> extractXmlRecords(
            String body,
            ResponseConfig responseConfig,
            List<IntegrationFieldMapping> mappings,
            ExecutionContext context) {

        Document document = pathExtractorService.parseXml(body);
        XPath xPath = XPathFactory.newInstance().newXPath();
        List<Node> nodes = selectNodes(document, xPath, recordPath(responseConfig));
        if (nodes.isEmpty()) {
            nodes = List.of(document.getDocumentElement());
        }

        List<LinkedHashMap<String, String>> records = new ArrayList<>();
        for (Node node : nodes) {
            MappingExpressionService.RecordAccessor recordAccessor = new XmlRecordAccessor(xPath, node);
            if (!matchesWindow(responseConfig, recordAccessor, context)) {
                continue;
            }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            for (IntegrationFieldMapping mapping : mappings) {
                String value = resolveMappingValue(mapping, recordAccessor, row, context);
                row.put(mapping.getTargetHeader(), applyFormatter(resolveValue(mapping, value), mapping.getFormatter()));
            }
            records.add(row);
        }
        return records;
    }

    private List<Node> selectNodes(Document document, XPath xPath, String recordPath) {
        if (!StringUtils.hasText(recordPath)) {
            return List.of();
        }
        try {
            NodeList nodeList = (NodeList) xPath.evaluate(recordPath, document, XPathConstants.NODESET);
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < nodeList.getLength(); i++) {
                nodes.add(nodeList.item(i));
            }
            return nodes;
        } catch (Exception ex) {
            throw new IntegrationFailureException(
                    FailureCategory.RESPONSE_PARSING_ERROR,
                    "Failed to extract XML records for path [" + recordPath + "]",
                    "RESPONSE_EXTRACTION",
                    null,
                    null,
                    null,
                    false,
                    ex);
        }
    }

    private String recordPath(ResponseConfig responseConfig) {
        return responseConfig == null ? null : responseConfig.getRecordPath();
    }

    private boolean matchesWindow(
            ResponseConfig responseConfig,
            MappingExpressionService.RecordAccessor recordAccessor,
            ExecutionContext context) {

        if (responseConfig == null
                || !responseConfig.isFilterByWindow()
                || !StringUtils.hasText(responseConfig.getRecordDatePath())
                || context == null) {
            return true;
        }

        ScheduleWindow scheduleWindow = context.getScheduleWindow();
        if (scheduleWindow == null
                || scheduleWindow.getWindowStart() == null
                || scheduleWindow.getWindowEnd() == null
                || !scheduleWindow.getWindowEnd().isAfter(scheduleWindow.getWindowStart())) {
            return true;
        }

        String rawValue = recordAccessor.value(responseConfig.getRecordDatePath(), responseConfig.getRecordDatePathType());
        if (!StringUtils.hasText(rawValue)) {
            return false;
        }

        LocalDateTime recordDateTime = parseRecordDate(rawValue, responseConfig.getRecordDateFormat());
        return !recordDateTime.isBefore(scheduleWindow.getWindowStart())
                && recordDateTime.isBefore(scheduleWindow.getWindowEnd());
    }

    private String resolveMappingValue(
            IntegrationFieldMapping mapping,
            MappingExpressionService.RecordAccessor recordAccessor,
            Map<String, String> currentRow,
            ExecutionContext context) {

        MappingType mappingType = mapping.getMappingType() == null
                ? MappingType.SOURCE_PATH
                : mapping.getMappingType();

        return switch (mappingType) {
            case SOURCE_PATH -> recordAccessor.value(mapping.getSourcePath(), mapping.getPathType());
            case CONSTANT -> mapping.getExpression();
            case EXPRESSION -> mappingExpressionService.evaluate(
                    mapping.getExpression(),
                    recordAccessor,
                    currentRow,
                    context);
        };
    }

    private String resolveValue(IntegrationFieldMapping mapping, String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : null;
        if (!StringUtils.hasText(normalized)) {
            normalized = mapping.getDefaultValue();
        }
        if (mapping.isRequiredFlag() && !StringUtils.hasText(normalized)) {
            throw new IntegrationFailureException(
                    FailureCategory.MAPPING_ERROR,
                    "Missing required mapping value for header " + mapping.getTargetHeader(),
                    "FIELD_MAPPING",
                    null,
                    null,
                    null,
                    false);
        }
        return normalized == null ? "" : normalized;
    }

    private String applyFormatter(String value, String formatter) {
        if (!StringUtils.hasText(formatter)) {
            return value;
        }
        String normalizedFormatter = formatter.trim();
        String uppercaseFormatter = normalizedFormatter.toUpperCase(Locale.ROOT);
        if (uppercaseFormatter.startsWith("DATE:")) {
            return applyTemporalFormatter(value, normalizedFormatter.substring(5));
        }
        if (uppercaseFormatter.startsWith("DATETIME:")) {
            return applyTemporalFormatter(value, normalizedFormatter.substring(9));
        }
        return switch (uppercaseFormatter) {
            case "UPPERCASE" -> value.toUpperCase(Locale.ROOT);
            case "LOWERCASE" -> value.toLowerCase(Locale.ROOT);
            case "TRIM" -> value.trim();
            default -> value;
        };
    }

    private String applyTemporalFormatter(String value, String formatterConfig) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        String config = formatterConfig == null ? "" : formatterConfig.trim();
        String inputPattern = null;
        String outputPattern = config;

        int separatorIndex = config.indexOf("->");
        if (separatorIndex >= 0) {
            inputPattern = config.substring(0, separatorIndex).trim();
            outputPattern = config.substring(separatorIndex + 2).trim();
        }

        if (!StringUtils.hasText(outputPattern)) {
            throw buildFormatterFailure(value, formatterConfig, "Formatter output pattern is required", null);
        }

        try {
            LocalDateTime parsedValue = parseTemporalValue(value, inputPattern);
            return DateTimeFormatter.ofPattern(outputPattern).format(parsedValue);
        } catch (IntegrationFailureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw buildFormatterFailure(value, formatterConfig, "Failed to format temporal value", ex);
        }
    }

    /*private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : stringify(list.get(0));
        }
        return String.valueOf(value);
    }*/

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private LocalDateTime parseRecordDate(String rawValue, String configuredFormat) {
        try {
            return parseTemporalValue(rawValue, configuredFormat);
        } catch (Exception ex) {
            throw buildRecordDateParseFailure(rawValue, configuredFormat, ex);
        }
    }

    private LocalDateTime parseTemporalValue(String rawValue, String configuredFormat) {
        if (StringUtils.hasText(configuredFormat)) {
            return toLocalDateTime(DateTimeFormatter.ofPattern(configuredFormat).parseBest(
                    rawValue.trim(),
                    LocalDateTime::from,
                    LocalDate::from,
                    OffsetDateTime::from,
                    Instant::from));
        }

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_INSTANT,
                DateTimeFormatter.ISO_LOCAL_DATE);
        for (DateTimeFormatter formatter : formatters) {
            try {
                TemporalAccessor accessor = formatter.parseBest(
                        rawValue.trim(),
                        LocalDateTime::from,
                        LocalDate::from,
                        OffsetDateTime::from,
                        Instant::from);
                return toLocalDateTime(accessor);
            } catch (Exception ignored) {
            }
        }

        throw new IllegalArgumentException("Failed to parse temporal value [" + rawValue + "]");
    }

    private LocalDateTime toLocalDateTime(TemporalAccessor accessor) {
        if (accessor instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (accessor instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        if (accessor instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        if (accessor instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        throw new IllegalArgumentException("Unsupported date temporal accessor: " + accessor.getClass().getSimpleName());
    }

    private IntegrationFailureException buildRecordDateParseFailure(String rawValue, String configuredFormat, Exception ex) {
        String message = StringUtils.hasText(configuredFormat)
                ? "Failed to parse record date [" + rawValue + "] using format [" + configuredFormat + "]"
                : "Failed to parse record date [" + rawValue + "]";
        return new IntegrationFailureException(
                FailureCategory.RESPONSE_PARSING_ERROR,
                message,
                "RESPONSE_WINDOW_FILTER",
                null,
                null,
                truncate(rawValue),
                false,
                ex);
    }

    private IntegrationFailureException buildFormatterFailure(
            String rawValue,
            String formatter,
            String message,
            Exception ex) {
        return new IntegrationFailureException(
                FailureCategory.MAPPING_ERROR,
                message + " value [" + rawValue + "] with formatter [" + formatter + "]",
                "FIELD_FORMATTER",
                null,
                null,
                truncate(rawValue),
                false,
                ex);
    }

    private static class JsonRecordAccessor implements MappingExpressionService.RecordAccessor {

        private final Object sourceRecord;

        private JsonRecordAccessor(Object sourceRecord) {
            this.sourceRecord = sourceRecord;
        }

        @Override
        public String value(String path, PathType pathType) {
            if (!StringUtils.hasText(path)) {
                return null;
            }
            try {
                Object result = JsonPath.read(sourceRecord, path);
                return stringifyResult(result);
            } catch (Exception ignored) {
                return null;
            }
        }

        @Override
        public java.math.BigDecimal number(String path, PathType pathType) {
            String value = value(path, pathType);
            if (!StringUtils.hasText(value)) {
                return java.math.BigDecimal.ZERO;
            }
            return new java.math.BigDecimal(value.trim());
        }

        private String stringifyResult(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof List<?> list) {
                return list.isEmpty() ? null : stringifyResult(list.get(0));
            }
            return String.valueOf(value);
        }
    }

    private static class XmlRecordAccessor implements MappingExpressionService.RecordAccessor {

        private final XPath xPath;
        private final Node node;

        private XmlRecordAccessor(XPath xPath, Node node) {
            this.xPath = xPath;
            this.node = node;
        }

        @Override
        public String value(String path, PathType pathType) {
            if (!StringUtils.hasText(path)) {
                return null;
            }
            try {
                String result = (String) xPath.evaluate(path, node, XPathConstants.STRING);
                return StringUtils.hasText(result) ? result.trim() : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        @Override
        public java.math.BigDecimal number(String path, PathType pathType) {
            String value = value(path, pathType);
            if (!StringUtils.hasText(value)) {
                return java.math.BigDecimal.ZERO;
            }
            return new java.math.BigDecimal(value.trim());
        }
    }
}
