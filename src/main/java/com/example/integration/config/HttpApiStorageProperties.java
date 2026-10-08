package com.example.integration.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.integration.model.enums.ScheduleType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "integration.storage.http-api")
public class HttpApiStorageProperties {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Optional shared URL template map used when storageConfig.uploadUrl is not set.
     * Keys should be schedule types like MONTHLY, HOURLY, or DAILY.
     * Values support the {tenantId} token.
     */
    private Map<String, String> uploadUrlTemplate = new LinkedHashMap<>();

    private String accessTokenHeaderName = "accessToken";
    private String userIdHeaderName = "userId";
    private Map<String, TenantProperties> tenants = new LinkedHashMap<>();
    private Map<String, TenantProperties> jsonTenants = new LinkedHashMap<>();
    private String tenantsJson;
    private String tenantsFile;
    private volatile long loadedTenantsFileModified = Long.MIN_VALUE;
    private volatile long loadedTenantsFileSize = Long.MIN_VALUE;
    private String defaultEvent = "FTP";
    private String defaultChannel = "daily";
    private String defaultIsSourceFileMoved = "True";
    private String defaultFileParam = "file";
    private String defaultPartyCodeField = "partyC_Code";

    public String resolveUploadUrlTemplate(ScheduleType scheduleType) {
        if (scheduleType == null || uploadUrlTemplate == null || uploadUrlTemplate.isEmpty()) {
            return null;
        }
        String target = scheduleType.name();
        return uploadUrlTemplate.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(target))
                .map(Map.Entry::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    public void setTenantsJson(String tenantsJson) {
        this.tenantsJson = tenantsJson;
        if (!StringUtils.hasText(tenantsJson)) {
            return;
        }
        try {
            Map<String, TenantProperties> parsed = OBJECT_MAPPER.readValue(
                    tenantsJson,
                    new TypeReference<LinkedHashMap<String, TenantProperties>>() {
                    });
            this.jsonTenants = parsed == null ? new LinkedHashMap<>() : parsed;
            this.tenants = this.jsonTenants;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse integration.storage.http-api.tenants-json", ex);
        }
    }

    /**
     * Returns tenant settings from the external file when configured. The file
     * is checked on every lookup, so replacing it updates the next upload
     * without restarting the application. The JSON environment value remains
     * the fallback when the file is absent.
     */
    public Map<String, TenantProperties> getTenants() {
        if (!StringUtils.hasText(tenantsFile)) {
            return tenants;
        }

        Path file = Path.of(tenantsFile);
        if (!Files.exists(file)) {
            loadedTenantsFileModified = Long.MIN_VALUE;
            loadedTenantsFileSize = Long.MIN_VALUE;
            return jsonTenants;
        }

        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            if (modified == loadedTenantsFileModified && size == loadedTenantsFileSize) {
                return tenants;
            }

            Map<String, TenantProperties> parsed = OBJECT_MAPPER.readValue(
                    Files.readString(file),
                    new TypeReference<LinkedHashMap<String, TenantProperties>>() {
                    });
            tenants = parsed == null ? new LinkedHashMap<>() : parsed;
            loadedTenantsFileModified = modified;
            loadedTenantsFileSize = size;
            return tenants;
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to load integration.storage.http-api.tenants-file: " + tenantsFile, ex);
        }
    }

    @Data
    public static class TenantProperties {
        /**
         * Optional tenant override for the upload URL.
         * Supports the {tenantId} token.
         */
        private String uploadUrl;
        private String accessToken;
        private String userId;
        @JsonProperty("Register_Id")
        private String registerId;
        private Map<String, String> headers = new LinkedHashMap<>();
        private Map<String, String> formFields = new LinkedHashMap<>();
    }
}
