package com.example.integration.service.storage;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.StorageType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StoredArtifact;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Uploads files using implicit FTPS (FTP over TLS from connection start). */
@Component
public class FtpsStorageProvider implements StorageProvider {

    @Override
    public StorageType getType() {
        return StorageType.FTPS;
    }

    @Override
    public StoredArtifact store(Path file, IntegrationDefinition definition, StorageConfig storageConfig, ScheduleWindow scheduleWindow) {
        validate(storageConfig);

        FTPSClient client = new FTPSClient(true);
        try (InputStream inputStream = Files.newInputStream(file)) {
            client.connect(storageConfig.getHost(), storageConfig.getPort());
            if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
                throw new IOException("FTPS server rejected connection: " + client.getReplyString());
            }
            if (!client.login(storageConfig.getUsername(), storageConfig.getPassword())) {
                throw new IOException("FTPS login failed: " + client.getReplyString());
            }

            client.execPBSZ(0);
            client.execPROT("P");
            if (Boolean.TRUE.equals(storageConfig.getPassiveMode())) {
                client.enterLocalPassiveMode();
            }
            client.setFileType(FTP.BINARY_FILE_TYPE);

            String remoteDirectory = StringUtils.hasText(storageConfig.getRemoteDirectory())
                    ? storageConfig.getRemoteDirectory().trim()
                    : "/";
            client.makeDirectory(remoteDirectory);
            if (!client.changeWorkingDirectory(remoteDirectory)) {
                throw new IOException("Unable to change to FTPS directory " + remoteDirectory + ": " + client.getReplyString());
            }

            if (!client.storeFile(file.getFileName().toString(), inputStream)) {
                throw new IOException("FTPS upload failed: " + client.getReplyString());
            }
            client.logout();
            return new StoredArtifact("ftps://" + storageConfig.getHost() + remoteDirectory + "/" + file.getFileName());
        } catch (IOException ex) {
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "Failed to upload file to implicit FTPS: " + ex.getMessage(),
                    "FILE_STORAGE",
                    "ftps://" + storageConfig.getHost(),
                    null,
                    null,
                    true,
                    ex);
        } finally {
            try {
                if (client.isConnected()) {
                    client.disconnect();
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
                    "FTPS storage requires host, username, and password",
                    "FILE_STORAGE",
                    null,
                    null,
                    null,
                    false);
        }
    }
}
