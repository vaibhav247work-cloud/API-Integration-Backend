package com.example.integration.service;

import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.entity.IntegrationFieldMapping;
import com.example.integration.model.enums.MappingType;
import com.example.integration.model.enums.PathType;
import com.example.integration.repository.IntegrationDefinitionRepository;
import com.example.integration.service.schedule.IntegrationSchedulingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationDefinitionServiceTest {

    @Mock
    private IntegrationDefinitionRepository integrationDefinitionRepository;

    @Mock
    private IntegrationSchedulingService integrationSchedulingService;

    @InjectMocks
    private IntegrationDefinitionService integrationDefinitionService;

    @Test
    void saveUpdatesManagedFieldMappingsInPlace() {
        IntegrationDefinition managed = new IntegrationDefinition();
        managed.setId(7L);
        managed.setClientName("Existing");
        managed.addMapping(mapping(1, "OLD_HEADER", "$.old"));
        List<IntegrationFieldMapping> originalCollection = managed.getFieldMappings();

        IntegrationDefinition request = new IntegrationDefinition();
        request.setId(7L);
        request.setClientName("Updated");
        request.setCsvFileName("updated.csv");
        request.setMaxRetries(2);
        request.setFieldMappings(new ArrayList<>(List.of(
                mapping(1, "Invoice_number", "$.BillNumber"),
                mapping(2, "Gross_Amount", "$.TotalAmount"))));

        when(integrationDefinitionRepository.findById(7L)).thenReturn(Optional.of(managed));
        when(integrationDefinitionRepository.save(any(IntegrationDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegrationDefinition saved = integrationDefinitionService.save(request);

        assertThat(saved.getFieldMappings()).isSameAs(originalCollection);
        assertThat(saved.getFieldMappings()).hasSize(2);
        assertThat(saved.getFieldMappings())
                .extracting(IntegrationFieldMapping::getTargetHeader)
                .containsExactly("Invoice_number", "Gross_Amount");
        assertThat(saved.getFieldMappings())
                .allMatch(mapping -> mapping.getIntegrationDefinition() == saved);
        assertThat(saved.getFieldMappings().get(0)).isNotSameAs(request.getFieldMappings().get(0));
        verify(integrationSchedulingService).refreshSchedule(saved);
    }

    private IntegrationFieldMapping mapping(int sortOrder, String targetHeader, String sourcePath) {
        IntegrationFieldMapping mapping = new IntegrationFieldMapping();
        mapping.setSortOrder(sortOrder);
        mapping.setMappingType(MappingType.SOURCE_PATH);
        mapping.setPathType(PathType.JSON_PATH);
        mapping.setTargetHeader(targetHeader);
        mapping.setSourcePath(sourcePath);
        return mapping;
    }
}
