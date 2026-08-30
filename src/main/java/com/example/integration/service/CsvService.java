package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.entity.IntegrationFieldMapping;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.runtime.ExecutionContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class CsvService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path tempDirectory;

    public CsvService(@Value("${integration.csv.temp-directory}") String tempDirectory) {
        this.tempDirectory = Path.of(tempDirectory);
    }

    public Path writeCsv(
            IntegrationDefinition definition,
            List<IntegrationFieldMapping> mappings,
            List<LinkedHashMap<String, String>> rows,
            ExecutionContext context) {

        try {
            Files.createDirectories(tempDirectory);
            String fileName = buildFileName(definition, context);
            Path file = tempDirectory.resolve(fileName);

            List<String> headers = mappings.stream()
                    .map(IntegrationFieldMapping::getTargetHeader)
                    .toList();

            try (Writer writer = Files.newBufferedWriter(file);
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                         .setHeader(headers.toArray(String[]::new))
                         .build())) {
                for (LinkedHashMap<String, String> row : rows) {
                    printer.printRecord(headers.stream().map(header -> row.getOrDefault(header, "")).toList());
                }
            }

            return file;
        } catch (IOException ex) {
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "Failed to write CSV file",
                    "CSV_WRITE",
                    null,
                    null,
                    null,
                    true,
                    ex);
        }
    }

    private String buildFileName(IntegrationDefinition definition, ExecutionContext context) {
        String brandCode = StringUtils.hasText(definition.getBrandCode())
                ? definition.getBrandCode()
                : definition.getClientName();
        String baseFileName = definition.getCsvFileName();
        String fileName = StringUtils.hasText(baseFileName) ? baseFileName : "integration-output.csv";
        int extensionIndex = fileName.lastIndexOf('.');
        String extension = extensionIndex < 0 ? ".csv" : fileName.substring(extensionIndex);
        String baseName = extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
        String fileToken = context.getScheduleWindow() == null
                ? FILE_TS.format(LocalDateTime.now())
                : context.getScheduleWindow().getFileToken();
        return sanitize(brandCode) + "_" + sanitize(baseName) + "_" + sanitize(fileToken) + extension;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
