package com.example.integration.service.storage;

import com.example.integration.config.HttpApiStorageProperties;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.StorageType;
import com.example.integration.model.runtime.ScheduleWindow;
import com.example.integration.model.runtime.StoredArtifact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class HttpApiStorageProvider implements StorageProvider {

    private final RestTemplate restTemplate;
    private final HttpApiStorageProperties httpApiStorageProperties;

    @Autowired
    public HttpApiStorageProvider(RestTemplateBuilder restTemplateBuilder,
                                  HttpApiStorageProperties httpApiStorageProperties) {
        this(restTemplateBuilder.build(), httpApiStorageProperties);
    }

    HttpApiStorageProvider(RestTemplate restTemplate,
                           HttpApiStorageProperties httpApiStorageProperties) {
        this.restTemplate = restTemplate;
        this.httpApiStorageProperties = httpApiStorageProperties;
    }

    @Override
    public StorageType getType() {
        return StorageType.HTTP_API;
    }

    @Override
    public StoredArtifact store(Path file, IntegrationDefinition definition, StorageConfig storageConfig, ScheduleWindow scheduleWindow) {
        StorageConfig effectiveConfig = storageConfig == null ? new StorageConfig() : storageConfig;
        ResolvedHttpUpload resolvedUpload = resolveUpload(effectiveConfig, scheduleWindow);
        validate(effectiveConfig, resolvedUpload);
        storageConfig = effectiveConfig;

        String uploadUrl  = resolvedUpload.uploadUrl();
        String method     = StringUtils.hasText(effectiveConfig.getUploadMethod())
                ? effectiveConfig.getUploadMethod().toUpperCase() : "POST";
        String fileParam  = StringUtils.hasText(effectiveConfig.getUploadFileParam())
                ? effectiveConfig.getUploadFileParam() : "file";
        String successText = storageConfig.getUploadSuccessText(); // nullable — optional check

        log.info("[HTTP_API] Uploading file '{}' to '{}' for client '{}'",
                file.getFileName(), uploadUrl, definition.getClientName());

        // Build multipart body
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (!CollectionUtils.isEmpty(resolvedUpload.formFields())) {
            for (Map.Entry<String, String> entry : resolvedUpload.formFields().entrySet()) {
                body.add(entry.getKey(), entry.getValue());
            }
        }
        body.add(fileParam, new FileSystemResource(file.toFile()));

        // Build headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (!CollectionUtils.isEmpty(resolvedUpload.headers())) {
            resolvedUpload.headers().forEach(headers::set);
        }

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl, HttpMethod.valueOf(method), request, String.class);

            String responseBody = response.getBody() != null ? response.getBody() : "";
            String responsePreview = responseBody.substring(0, Math.min(500, responseBody.length()));

            // Check HTTP status
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("[HTTP_API] Upload failed for client '{}' — HTTP {} — URL: {} — Response: {}",
                        definition.getClientName(), response.getStatusCode().value(), uploadUrl, responsePreview);
                throw new IntegrationFailureException(
                        FailureCategory.STORAGE_ERROR,
                        "HTTP API upload failed with status " + response.getStatusCode()
                                + " for " + file.getFileName(),
                        "FILE_STORAGE",
                        uploadUrl,
                        response.getStatusCode().value(),
                        responsePreview,
                        true);
            }

            // If uploadSuccessText is configured, verify it appears in the response body
            if (StringUtils.hasText(successText) && !responseBody.contains(successText)) {
                log.error("[HTTP_API] Upload response did not contain expected success text '{}' for client '{}' — URL: {} — Response: {}",
                        successText, definition.getClientName(), uploadUrl, responsePreview);
                throw new IntegrationFailureException(
                        FailureCategory.STORAGE_ERROR,
                        "HTTP API upload response did not contain expected success text: '" + successText + "'",
                        "FILE_STORAGE",
                        uploadUrl,
                        response.getStatusCode().value(),
                        responsePreview,
                        true);
            }

            log.info("[HTTP_API] File '{}' uploaded successfully to '{}' for client '{}'",
                    file.getFileName(), uploadUrl, definition.getClientName());
            return new StoredArtifact(uploadUrl);

        } catch (IntegrationFailureException ex) {
            throw ex; // already logged above, re-throw as-is

        } catch (RestClientResponseException ex) {
            String responsePreview = ex.getResponseBodyAsString()
                    .substring(0, Math.min(500, ex.getResponseBodyAsString().length()));
            log.error("[HTTP_API] Upload failed for client '{}' — HTTP {} — URL: {} — Response: {}",
                    definition.getClientName(), ex.getStatusCode().value(), uploadUrl, responsePreview, ex);
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "HTTP API upload failed: " + ex.getMessage(),
                    "FILE_STORAGE",
                    uploadUrl,
                    ex.getStatusCode().value(),
                    responsePreview,
                    isRetryable(ex.getStatusCode().value()),
                    ex);

        } catch (Exception ex) {
            log.error("[HTTP_API] Upload failed for client '{}' — URL: {} — Error: {}",
                    definition.getClientName(), uploadUrl, ex.getMessage(), ex);
            throw new IntegrationFailureException(
                    FailureCategory.STORAGE_ERROR,
                    "Failed to upload file via HTTP API: " + ex.getMessage(),
                    "FILE_STORAGE",
                    uploadUrl,
                    null,
                    null,
                    true,
                    ex);
        }
    }

    private ResolvedHttpUpload resolveUpload(StorageConfig storageConfig, ScheduleWindow scheduleWindow) {
        if (storageConfig == null) {
            return new ResolvedHttpUpload(null, Map.of(), Map.of());
        }

        Map<String, String> resolvedHeaders = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(storageConfig.getUploadHeaders())) {
            resolvedHeaders.putAll(storageConfig.getUploadHeaders());
        }

        Map<String, String> resolvedFormFields = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(storageConfig.getUploadFormFields())) {
            resolvedFormFields.putAll(storageConfig.getUploadFormFields());
        }

        String uploadUrl = storageConfig.getUploadUrl();
        String scheduleUploadUrl = httpApiStorageProperties.resolveUploadUrlTemplate(
                scheduleWindow == null ? null : scheduleWindow.getScheduleType());
        if (!StringUtils.hasText(storageConfig.getTenantId())) {
            if (!StringUtils.hasText(uploadUrl)) {
                uploadUrl = scheduleUploadUrl;
            }
            return new ResolvedHttpUpload(uploadUrl, resolvedHeaders, resolvedFormFields);
        }

        HttpApiStorageProperties.TenantProperties tenantProperties =
                httpApiStorageProperties.getTenants().get(storageConfig.getTenantId());
        if (tenantProperties == null) {
            throw new IntegrationFailureException(
                    FailureCategory.CONFIGURATION_ERROR,
                    "HTTP_API storage tenantId '" + storageConfig.getTenantId() + "' is not configured in properties",
                    "FILE_STORAGE",
                    null, null, null, false);
        }

        if (!StringUtils.hasText(uploadUrl)) {
            uploadUrl = StringUtils.hasText(tenantProperties.getUploadUrl())
                    ? tenantProperties.getUploadUrl()
                    : scheduleUploadUrl;
        }
        uploadUrl = replaceTenantId(uploadUrl, storageConfig.getTenantId());

        if (StringUtils.hasText(tenantProperties.getAccessToken())) {
            resolvedHeaders.put(httpApiStorageProperties.getAccessTokenHeaderName(), tenantProperties.getAccessToken());
        }
        if (StringUtils.hasText(tenantProperties.getUserId())) {
            resolvedHeaders.put(httpApiStorageProperties.getUserIdHeaderName(), tenantProperties.getUserId());
        }
        if (!CollectionUtils.isEmpty(tenantProperties.getHeaders())) {
            resolvedHeaders.putAll(tenantProperties.getHeaders());
        }
        if (!CollectionUtils.isEmpty(tenantProperties.getFormFields())) {
            resolvedFormFields.putAll(tenantProperties.getFormFields());
        }

        return new ResolvedHttpUpload(uploadUrl, resolvedHeaders, resolvedFormFields);
    }

    private String replaceTenantId(String value, String tenantId) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.replace("{tenantId}", tenantId);
    }

    private void validate(StorageConfig storageConfig, ResolvedHttpUpload resolvedUpload) {
        if (storageConfig == null || !StringUtils.hasText(resolvedUpload.uploadUrl())) {
            throw new IntegrationFailureException(
                    FailureCategory.CONFIGURATION_ERROR,
                    "HTTP_API storage requires uploadUrl or tenant-based HTTP_API properties to be configured",
                    "FILE_STORAGE",
                    null, null, null, false);
        }
    }

    private record ResolvedHttpUpload(
            String uploadUrl,
            Map<String, String> headers,
            Map<String, String> formFields) {
    }

    /**
     * 5xx server errors and 429 rate limit are retryable.
     * 4xx client errors (bad token, wrong URL) are not retried.
     */
    private boolean isRetryable(int httpStatus) {
        return httpStatus == 429 || (httpStatus >= 500 && httpStatus < 600);
    }
}
