package com.example.integration.service.storage;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.StorageType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StoredArtifact;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FtpStorageProvider implements StorageProvider {

    @Override
    public StorageType getType() {
        return StorageType.FTP;
    }

    @Override
    public StoredArtifact store(Path file, IntegrationDefinition definition, StorageConfig storageConfig, ScheduleWindow scheduleWindow) {
        validate(storageConfig);

        FTPClient ftpClient = new FTPClient();
        try (InputStream inputStream = Files.newInputStream(file)) {
            ftpClient.connect(storageConfig.getHost(), storageConfig.getPort());
            ftpClient.login(storageConfig.getUsername(), storageConfig.getPassword());
            if (Boolean.TRUE.equals(storageConfig.getPassiveMode())) {
                ftpClient.enterLocalPassiveMode();
            }
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            String remoteDirectory = StringUtils.hasText(storageConfig.getRemoteDirectory())
                    ? storageConfig.getRemoteDirectory()
                    : "/";
            ftpClient.makeDirectory(remoteDirectory);
            ftpClient.changeWorkingDirectory(remoteDirectory);

            boolean stored = ftpClient.storeFile(file.getFileName().toString(), inputStream);
            if (!stored) {
                throw new IntegrationFailureException(
                        FailureCategory.STORAGE_ERROR,
                        "FTP upload failed for " + file.getFileName(),
                        "FILE_STORAGE",
                        "ftp://" + storageConfig.getHost(),
                        null,
                        null,
                        true);
            }
            ftpClient.logout();

            return new StoredArtifact("ftp://" + storageConfig.getHost() + remoteDirectory + "/" + file.getFileName());
        } catch (IOException ex) {
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "Failed to upload file to FTP",
                    "FILE_STORAGE",
                    "ftp://" + storageConfig.getHost(),
                    null,
                    null,
                    true,
                    ex);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void validate(StorageConfig storageConfig) {
        if (storageConfig == null
                || !StringUtils.hasText(storageConfig.getHost())
                || !StringUtils.hasText(storageConfig.getUsername())
                || !StringUtils.hasText(storageConfig.getPassword())) {
            throw new IntegrationFailureException(
                    FailureCategory.CONFIGURATION_ERROR,
                    "FTP storage requires host, username, and password",
                    "FILE_STORAGE",
                    null,
                    null,
                    null,
                    false);
        }
    }
}
