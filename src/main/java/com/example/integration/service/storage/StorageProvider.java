package com.example.integration.service.storage;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.StorageType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StoredArtifact;

import java.nio.file.Path;

public interface StorageProvider {
    StorageType getType();

    StoredArtifact store(Path file, IntegrationDefinition definition, StorageConfig storageConfig, ScheduleWindow scheduleWindow);
}
