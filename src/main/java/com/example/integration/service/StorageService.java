package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.StorageType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StoredArtifact;
import com.example.integration.service.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final java.util.List<StorageProvider> storageProviders;
    private final ConfigBindingService configBindingService;

    public StoredArtifact store(IntegrationDefinition definition, Path file, ScheduleWindow scheduleWindow) {
        StorageConfig storageConfig = configBindingService.getStorageConfig(definition);
        StorageType storageType = storageConfig == null ? StorageType.LOCAL : storageConfig.getType();

        Map<StorageType, StorageProvider> providers = storageProviders.stream()
                .collect(Collectors.toMap(StorageProvider::getType, Function.identity()));
        StorageProvider provider = providers.getOrDefault(storageType, providers.get(StorageType.LOCAL));

        if (provider == null) {
            throw new IllegalStateException("No storage provider registered for " + storageType);
        }
        return provider.store(file, definition, storageConfig, scheduleWindow);
    }
}
