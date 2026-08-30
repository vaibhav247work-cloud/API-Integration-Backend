package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.model.config.AuthConfig;
import com.example.integration.model.config.PaginationConfig;
import com.example.integration.model.config.ResponseConfig;
import com.example.integration.model.config.ScheduleDefinition;
import com.example.integration.model.config.StepConfig;
import com.example.integration.model.config.StorageConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigBindingService {

    private final ObjectMapper objectMapper;

    public AuthConfig getAuthConfig(IntegrationDefinition definition) {
        return bind(definition.getAuthConfig(), AuthConfig.class);
    }

    public PaginationConfig getPaginationConfig(IntegrationDefinition definition) {
        return bind(definition.getPaginationConfig(), PaginationConfig.class);
    }

    public ResponseConfig getResponseConfig(IntegrationDefinition definition) {
        return bind(definition.getResponseConfig(), ResponseConfig.class);
    }

    public StorageConfig getStorageConfig(IntegrationDefinition definition) {
        return bind(definition.getStorageConfig(), StorageConfig.class);
    }

    public List<ScheduleDefinition> getScheduleDefinitions(IntegrationDefinition definition) {
        List<ScheduleDefinition> schedules = bindList(definition.getScheduleConfig(), new TypeReference<>() {});
        return schedules.stream()
                .filter(schedule -> schedule.getType() != null)
                .toList();
    }

    public List<StepConfig> getStepConfigs(IntegrationDefinition definition) {
        List<StepConfig> stepConfigs = bindList(definition.getStepConfig(), new TypeReference<>() {});
        if (!stepConfigs.isEmpty()) {
            stepConfigs.sort(Comparator.comparingInt(step -> step.getOrderIndex() == null ? 0 : step.getOrderIndex()));
            return stepConfigs;
        }

        StepConfig fallback = bind(definition.getRequestConfig(), StepConfig.class);
        if (fallback == null) {
            return List.of();
        }

        if (!StringUtils.hasText(fallback.getUrl())) {
            fallback.setUrl(definition.getBaseUrl());
        }
        if (fallback.getDataStep() == null) {
            fallback.setDataStep(true);
        }
        if (fallback.getEnabled() == null) {
            fallback.setEnabled(true);
        }
        return List.of(fallback);
    }

    public <T> T bind(JsonNode node, Class<T> type) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, type);
    }

    public <T> List<T> bindList(JsonNode node, TypeReference<List<T>> typeReference) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        return objectMapper.convertValue(node, typeReference);
    }
}
