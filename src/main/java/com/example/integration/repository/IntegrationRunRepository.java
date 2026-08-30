package com.example.integration.repository;

import com.example.integration.entity.IntegrationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationRunRepository extends JpaRepository<IntegrationRun, Long> {
    List<IntegrationRun> findByIntegrationIdOrderByStartedAtDesc(Long integrationId);
}
