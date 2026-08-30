package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.entity.IntegrationFieldMapping;
import com.example.integration.model.enums.IntegrationSchedulerMode;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.service.schedule.IntegrationSchedulingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IntegrationDefinitionService {

    private final IntegrationDefinitionRepository integrationDefinitionRepository;
    private final IntegrationSchedulingService integrationSchedulingService;

    public List<IntegrationDefinition> findAll() {
        return integrationDefinitionRepository.findAll();
    }

    public IntegrationDefinition findById(Long id) {
        return integrationDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + id));
    }

    @Transactional
    public IntegrationDefinition save(IntegrationDefinition definition) {
        IntegrationDefinition target = definition.getId() == null
                ? new IntegrationDefinition()
                : integrationDefinitionRepository.findById(definition.getId())
                .orElseThrow(() -> new IllegalArgumentException("Integration not found: " + definition.getId()));

        copyDefinition(definition, target);

        target.replaceMappings(copyMappings(definition.getFieldMappings()));

        IntegrationDefinition saved = integrationDefinitionRepository.save(target);
        integrationSchedulingService.refreshSchedule(saved);
        return saved;
    }

    @Transactional
    public IntegrationDefinition setEnabled(Long id, boolean enabled) {
        IntegrationDefinition definition = findById(id);
        definition.setEnabled(enabled);
        IntegrationDefinition saved = integrationDefinitionRepository.save(definition);
        integrationSchedulingService.refreshSchedule(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        integrationDefinitionRepository.deleteById(id);
        integrationSchedulingService.removeSchedule(id);
    }

    private void copyDefinition(IntegrationDefinition source, IntegrationDefinition target) {
        target.setClientName(source.getClientName());
        target.setBrandCode(source.getBrandCode());
        target.setBaseUrl(source.getBaseUrl());
        target.setEnabled(source.isEnabled());
        target.setScheduleCron(source.getScheduleCron());
        target.setScheduleConfig(source.getScheduleConfig());
        target.setCsvFileName(source.getCsvFileName());
        target.setOutputDirectory(source.getOutputDirectory());
        target.setMaxRetries(source.getMaxRetries());
        target.setAuthConfig(source.getAuthConfig());
        target.setRequestConfig(source.getRequestConfig());
        target.setResponseConfig(source.getResponseConfig());
        target.setPaginationConfig(source.getPaginationConfig());
        target.setStorageConfig(source.getStorageConfig());
        target.setStepConfig(source.getStepConfig());
        target.setSchedulerMode(source.getSchedulerMode() == null
                ? (target.getSchedulerMode() == null ? IntegrationSchedulerMode.LEGACY : target.getSchedulerMode())
                : source.getSchedulerMode());
    }

    private List<IntegrationFieldMapping> copyMappings(List<IntegrationFieldMapping> sourceMappings) {
        if (sourceMappings == null || sourceMappings.isEmpty()) {
            return List.of();
        }

        List<IntegrationFieldMapping> copiedMappings = new ArrayList<>();
        for (IntegrationFieldMapping source : sourceMappings) {
            IntegrationFieldMapping copy = new IntegrationFieldMapping();
            copy.setSortOrder(source.getSortOrder());
            copy.setMappingType(source.getMappingType());
            copy.setSourcePath(source.getSourcePath());
            copy.setPathType(source.getPathType());
            copy.setTargetHeader(source.getTargetHeader());
            copy.setExpression(source.getExpression());
            copy.setDefaultValue(source.getDefaultValue());
            copy.setFormatter(source.getFormatter());
            copy.setRequiredFlag(source.isRequiredFlag());
            copiedMappings.add(copy);
        }
        return copiedMappings;
    }
}
