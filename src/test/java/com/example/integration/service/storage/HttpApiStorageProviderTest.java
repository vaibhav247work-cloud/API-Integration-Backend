package com.example.integration.service.storage;

import com.example.integration.config.HttpApiStorageProperties;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.exception.IntegrationFailureException;
import com.example.integration.model.config.StorageConfig;
import com.example.integration.model.enums.FailureCategory;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.model.runtime.ScheduleWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpApiStorageProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void storeResolvesTenantUrlAndHeadersFromProperties() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        HttpApiStorageProperties properties = new HttpApiStorageProperties();
        properties.setUploadUrlTemplate(new LinkedHashMap<>(Map.of(
                "HOURLY", "https://storage.example.com/uploadFile?tenantId={tenantId}",
                "MONTHLY", "https://storage-monthly.example.com/uploadFile?tenantId={tenantId}")));
        HttpApiStorageProperties.TenantProperties tenantProperties = new HttpApiStorageProperties.TenantProperties();
        tenantProperties.setAccessToken("token-a");
        tenantProperties.setUserId("user-a");
        tenantProperties.setHeaders(Map.of("tenantHeader", "tenant-value"));
        tenantProperties.setFormFields(Map.of("partyC_Code", "party-a"));
        properties.setTenants(new LinkedHashMap<>(Map.of("tenant-a", tenantProperties)));

        HttpApiStorageProvider provider = new HttpApiStorageProvider(restTemplate, properties);
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setTenantId("tenant-a");
        storageConfig.setUploadHeaders(Map.of("Event", "ftp"));

        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setClientName("Tenant Upload Client");

        Path file = Files.writeString(tempDir.resolve("orders.csv"), "id,name\n1,test\n");

        server.expect(once(), requestTo("https://storage.example.com/uploadFile?tenantId=tenant-a"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Event", "ftp"))
                .andExpect(header("accessToken", "token-a"))
                .andExpect(header("userId", "user-a"))
                .andExpect(header("tenantHeader", "tenant-value"))
                .andRespond(withSuccess("uploaded", MediaType.TEXT_PLAIN));

        assertThat(provider.store(file, definition, storageConfig, hourlyWindow()).location())
                .isEqualTo("https://storage.example.com/uploadFile?tenantId=tenant-a");

        server.verify();
    }

    @Test
    void storeFailsWhenTenantIdIsUnknown() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpApiStorageProperties properties = new HttpApiStorageProperties();
        properties.setUploadUrlTemplate(new LinkedHashMap<>(Map.of(
                "HOURLY", "https://storage.example.com/uploadFile?tenantId={tenantId}")));

        HttpApiStorageProvider provider = new HttpApiStorageProvider(restTemplate, properties);
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setTenantId("missing-tenant");

        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setClientName("Tenant Upload Client");

        Path file = Files.writeString(tempDir.resolve("orders.csv"), "id,name\n1,test\n");

        assertThatThrownBy(() -> provider.store(file, definition, storageConfig, hourlyWindow()))
                .isInstanceOf(IntegrationFailureException.class)
                .satisfies(ex -> {
                    IntegrationFailureException failure = (IntegrationFailureException) ex;
                    assertThat(failure.getFailureCategory()).isEqualTo(FailureCategory.CONFIGURATION_ERROR);
                    assertThat(failure.getMessage()).contains("missing-tenant");
                });
    }

    @Test
    void storeResolvesScheduleSpecificUrlWithoutTenant() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        HttpApiStorageProperties properties = new HttpApiStorageProperties();
        properties.setUploadUrlTemplate(new LinkedHashMap<>(Map.of(
                "MONTHLY", "https://monthly-storage.example.com/uploadFile",
                "HOURLY", "https://hourly-storage.example.com/uploadFile")));

        HttpApiStorageProvider provider = new HttpApiStorageProvider(restTemplate, properties);
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setUploadHeaders(Map.of("Event", "ftp"));

        IntegrationDefinition definition = new IntegrationDefinition();
        definition.setClientName("Schedule Upload Client");

        Path file = Files.writeString(tempDir.resolve("monthly.csv"), "id,name\n1,test\n");

        server.expect(once(), requestTo("https://monthly-storage.example.com/uploadFile"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Event", "ftp"))
                .andRespond(withSuccess("uploaded", MediaType.TEXT_PLAIN));

        assertThat(provider.store(file, definition, storageConfig, monthlyWindow()).location())
                .isEqualTo("https://monthly-storage.example.com/uploadFile");

        server.verify();
    }

    private ScheduleWindow hourlyWindow() {
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.HOURLY)
                .triggerTime(LocalDateTime.of(2026, 3, 20, 14, 0))
                .windowStart(LocalDateTime.of(2026, 3, 20, 13, 0))
                .windowEnd(LocalDateTime.of(2026, 3, 20, 14, 0))
                .fileToken("hourly_2026032013")
                .build();
    }

    private ScheduleWindow monthlyWindow() {
        return ScheduleWindow.builder()
                .scheduleType(ScheduleType.MONTHLY)
                .triggerTime(LocalDateTime.of(2026, 4, 1, 1, 0))
                .windowStart(LocalDateTime.of(2026, 3, 1, 0, 0))
                .windowEnd(LocalDateTime.of(2026, 4, 1, 0, 0))
                .fileToken("monthly_202603")
                .build();
    }
}
