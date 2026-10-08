package com.example.integration.service.storage;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.StorageType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StoredArtifact;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Component
public class SftpStorageProvider implements StorageProvider {

    private static final int CONNECTION_TIMEOUT_MILLIS = 30_000;

    @Override
    public StorageType getType() {
        return StorageType.SFTP;
    }

    @Override
    public StoredArtifact store(
            Path file,
            IntegrationDefinition definition,
            StorageConfig storageConfig,
            ScheduleWindow scheduleWindow) {
        validate(storageConfig);

        Session session = null;
        ChannelSftp channel = null;
        try (InputStream inputStream = Files.newInputStream(file)) {
            JSch jSch = new JSch();
            session = jSch.getSession(
                    storageConfig.getUsername(),
                    storageConfig.getHost(),
                    storageConfig.getPort());
            session.setPassword(storageConfig.getPassword());

            Properties sessionConfig = new Properties();
            sessionConfig.put("StrictHostKeyChecking", "no");
            session.setConfig(sessionConfig);
            session.connect(CONNECTION_TIMEOUT_MILLIS);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECTION_TIMEOUT_MILLIS);

            String remoteDirectory = StringUtils.hasText(storageConfig.getRemoteDirectory())
                    ? storageConfig.getRemoteDirectory().trim()
                    : ".";
            changeToDirectory(channel, remoteDirectory);
            channel.put(inputStream, file.getFileName().toString(), ChannelSftp.OVERWRITE);

            return new StoredArtifact(
                    "sftp://" + storageConfig.getHost() + remoteDirectory + "/" + file.getFileName());
        } catch (JSchException | SftpException | java.io.IOException ex) {
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "Failed to upload file to SFTP: " + ex.getMessage(),
                    "FILE_STORAGE",
                    "sftp://" + storageConfig.getHost(),
                    null,
                    null,
                    true,
                    ex);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private void changeToDirectory(ChannelSftp channel, String remoteDirectory) throws SftpException {
        if (".".equals(remoteDirectory)) {
            return;
        }

        boolean absolute = remoteDirectory.startsWith("/");
        String current = absolute ? "/" : ".";
        channel.cd(current);
        for (String part : remoteDirectory.split("/")) {
            if (!StringUtils.hasText(part) || ".".equals(part)) {
                continue;
            }
            try {
                channel.cd(part);
            } catch (SftpException missingDirectory) {
                channel.mkdir(part);
                channel.cd(part);
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
                    "SFTP storage requires host, username, and password",
                    "FILE_STORAGE",
                    null,
                    null,
                    null,
                    false);
        }
    }
}
