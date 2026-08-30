package com.example.integration.service.storage;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.StorageType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StoredArtifact;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class LocalStorageProvider implements StorageProvider {

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    @Override
    public StoredArtifact store(Path file, IntegrationDefinition definition, StorageConfig storageConfig, ScheduleWindow scheduleWindow) {
        try {
            String configuredDirectory = storageConfig != null ? storageConfig.getLocalDirectory() : null;
            String outputDirectory = StringUtils.hasText(configuredDirectory)
                    ? configuredDirectory
                    : definition.getOutputDirectory();
            Path directory = Path.of(StringUtils.hasText(outputDirectory) ? outputDirectory : "output");
            Files.createDirectories(directory);
            Path target = directory.resolve(file.getFileName());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredArtifact(target.toAbsolutePath().toString());
        } catch (IOException ex) {
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "Failed to store file locally",
                    "FILE_STORAGE",
                    resolveTargetLocation(storageConfig, definition),
                    null,
                    null,
                    true,
                    ex);
        }
    }

    private String resolveTargetLocation(StorageConfig storageConfig, IntegrationDefinition definition) {
        String configuredDirectory = storageConfig != null ? storageConfig.getLocalDirectory() : null;
        String outputDirectory = StringUtils.hasText(configuredDirectory)
                ? configuredDirectory
                : definition.getOutputDirectory();
        return StringUtils.hasText(outputDirectory) ? outputDirectory : "output";
    }
}
