package com.example.integration.repository;

import com.example.integration.entity.IntegrationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationDefinitionRepository extends JpaRepository<IntegrationDefinition, Long> {
    List<IntegrationDefinition> findByEnabledTrue();
}
