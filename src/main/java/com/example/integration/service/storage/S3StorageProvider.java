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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;

@Component
public class S3StorageProvider implements StorageProvider {

    @Override
    public StorageType getType() {
        return StorageType.S3;
    }

    @Override
    public StoredArtifact store(Path file, IntegrationDefinition definition, StorageConfig storageConfig, ScheduleWindow scheduleWindow) {
        if (storageConfig == null || !StringUtils.hasText(storageConfig.getBucket()) || !StringUtils.hasText(storageConfig.getRegion())) {
            throw new IntegrationFailureException(
                    FailureCategory.CONFIGURATION_ERROR,
                    "S3 storage requires bucket and region",
                    "FILE_STORAGE",
                    null,
                    null,
                    null,
                    false);
        }

        String keyPrefix = StringUtils.hasText(storageConfig.getKeyPrefix()) ? storageConfig.getKeyPrefix().trim() : "";
        String key = keyPrefix.isEmpty() ? file.getFileName().toString() : keyPrefix + "/" + file.getFileName();

        try (S3Client s3Client = S3Client.builder().region(Region.of(storageConfig.getRegion())).build()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .key(key)
                    .build();
            s3Client.putObject(request, file);
            return new StoredArtifact("s3://" + storageConfig.getBucket() + "/" + key);
        } catch (Exception ex) {
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "Failed to upload file to S3",
                    "FILE_STORAGE",
                    "s3://" + storageConfig.getBucket() + "/" + key,
                    null,
                    null,
                    true,
                    ex);
        }
    }
}
