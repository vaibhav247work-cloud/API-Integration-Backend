package com.example.integration.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.integration.model.enums.ScheduleType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

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
    private String tenantsJson;

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
            this.tenants = parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse integration.storage.http-api.tenants-json", ex);
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
        private Map<String, String> headers = new LinkedHashMap<>();
        private Map<String, String> formFields = new LinkedHashMap<>();
    }
}
