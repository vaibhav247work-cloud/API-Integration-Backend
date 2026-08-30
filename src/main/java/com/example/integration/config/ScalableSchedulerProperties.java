package com.example.integration.config;

import com.example.integration.model.enums.JobStoreVendor;
import com.example.integration.model.enums.MigrationMode;
import com.example.integration.model.enums.RuntimeRole;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "integration.scalable")
public class ScalableSchedulerProperties {

    private Set<RuntimeRole> roles = EnumSet.allOf(RuntimeRole.class);
    private MigrationMode migrationMode = MigrationMode.QUEUE;
    private JobStoreVendor jobStoreVendor = JobStoreVendor.MYSQL;
    private Worker worker = new Worker();

    public boolean hasRole(RuntimeRole role) {
        return roles != null && roles.contains(role);
    }

    @Getter
    @Setter
    public static class Worker {
        private int batchSize = 64;
        private int maxConcurrency = 128;
        private int queueCapacity = 2048;
        private long pollIntervalMs = 1000;
        private long leaseSeconds = 300;
        private long heartbeatSeconds = 15;
    }
}
